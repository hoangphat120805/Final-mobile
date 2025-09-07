package com.example.vaicheuserapp.ui.sell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.vaicheuserapp.R
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.CoordinateBounds
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion
import com.mapbox.search.ui.adapter.autocomplete.PlaceAutocompleteUiAdapter
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.view.SearchResultsView
import kotlinx.coroutines.launch
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

class LocationPickerDialog : DialogFragment() {

    interface OnLocationSelectedListener {
        fun onLocationSelected(address: String, latitude: Double, longitude: Double)
    }

    private var suppressSearch: Boolean = false
    private lateinit var mapView: MapView
    private lateinit var searchBar: EditText
    private lateinit var searchResultsView: SearchResultsView
    private lateinit var placeAutocomplete: PlaceAutocomplete

    private var selectedAddress: String? = null
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null

    // annotation manager and last annotation reference
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var currentAnnotation: PointAnnotation? = null

    private lateinit var listener: OnLocationSelectedListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = when {
            parentFragment is OnLocationSelectedListener -> parentFragment as OnLocationSelectedListener
            context is OnLocationSelectedListener -> context
            else -> throw RuntimeException("Activity or parent fragment must implement OnLocationSelectedListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // put your Mapbox token in strings.xml and reference it here
        MapboxOptions.accessToken = getString(R.string.mapbox_access_token)
        placeAutocomplete = PlaceAutocomplete.create()
        setStyle(DialogFragment.STYLE_NORMAL, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Use the layout you already have (ensure IDs match)
        return inflater.inflate(R.layout.fragment_location_picker_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        mapView = view.findViewById(R.id.map_view)
        searchBar = view.findViewById(R.id.search_bar)
        searchResultsView = view.findViewById(R.id.search_results_view)
        val confirmButton: Button = view.findViewById(R.id.btnConfirm)

        // Ensure full-screen dialog and adjust for keyboard
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Map init
        val mapboxMap = mapView.getMapboxMap()
        // load style and when style is loaded create annotation manager
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS) { style ->
            // create the annotation manager only after style loaded
            pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
        }

        // set initial camera to HCMC and restrict bounds to Vietnam area
        val hcm = Point.fromLngLat(106.7009, 10.7769)
        mapboxMap.setCamera(CameraOptions.Builder().center(hcm).zoom(11.0).build())

        val sw = Point.fromLngLat(102.0, 8.0)
        val ne = Point.fromLngLat(110.6, 23.5)
        val bounds = CoordinateBounds(sw, ne)
        // Restrict how far out users can pan/zoom (keeps focus on Vietnam)
        try {
            mapboxMap.setBounds(
                com.mapbox.maps.CameraBoundsOptions.Builder()
                    .bounds(bounds)
                    .minZoom(5.0)
                    .maxZoom(18.0)
                    .build()
            )
        } catch (e: Exception) {
            // some older versions may throw; safe to ignore or log
        }

        // Initialize SearchResultsView with CommonSearchViewConfiguration
        val commonConfig = CommonSearchViewConfiguration()
        val resultsConfig = SearchResultsView.Configuration(commonConfig)
        searchResultsView.initialize(resultsConfig)

        // Create adapter AFTER results view is initialized
        val adapter = PlaceAutocompleteUiAdapter(view = searchResultsView, placeAutocomplete = placeAutocomplete)

        // Make searchBar friendly to autocorrect/suggestions
        searchBar.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
        searchBar.isSingleLine = true
        searchBar.isFocusable = true
        searchBar.isFocusableInTouchMode = true

        // Text watcher -> adapter.search (suspend)
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (suppressSearch) return  // <-- ignore programmatic changes

                val query = s.toString()
                if (query.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            adapter.search(query) // suspend
                            searchResultsView.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Search error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    searchResultsView.visibility = View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable) {}
        })

        searchBar.setOnTouchListener { v, event ->
            // user touched -> enable searching and let the normal behavior (keyboard) continue
            suppressSearch = false
            false // return false so the touch still places cursor and opens keyboard
        }

// also re-enable when the EditText receives focus (covers programmatic focus change)
        searchBar.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) suppressSearch = false
        }

        // When a suggestion is selected in the UI list
        adapter.addSearchListener(object : PlaceAutocompleteUiAdapter.SearchListener {
            override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {
                lifecycleScope.launch {
                    try {
                        val selectionResponse = placeAutocomplete.select(suggestion)
                        selectionResponse.onValue { result ->
                            val point: Point? = result.coordinate
                            if (point != null) {
                                // move camera and show pin
                                mapboxMap.setCamera(CameraOptions.Builder().center(point).zoom(14.0).build())

                                // formatted address (String?)
                                val formatted = result.address?.formattedAddress ?: suggestion.name

                                // show pin at coordinate
                                showPinAt(point)

                                // write final address back to search bar
                                suppressSearch = true
                                searchBar.setText(formatted)
                                // move cursor to end
                                searchBar.setSelection(formatted.length)

                                // store choice
                                selectedAddress = formatted
                                selectedLat = point.latitude()
                                selectedLng = point.longitude()

                                // hide suggestions and keyboard, clear focus so map visible
                                searchResultsView.visibility = View.GONE
                                hideKeyboardAndClearFocus()
                            } else {
                                Toast.makeText(requireContext(), "Coordinates not available", Toast.LENGTH_SHORT).show()
                            }
                        }.onError { err ->
                            Toast.makeText(requireContext(), "Select error: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Selection exception: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) { /* no-op */ }

            override fun onPopulateQueryClick(suggestion: PlaceAutocompleteSuggestion) {
                // If user taps populate, we mirror name into search field (keeps UX)
                searchBar.setText(suggestion.name)
                searchBar.setSelection(suggestion.name.length)
            }

            override fun onError(e: Exception) {
                Toast.makeText(requireContext(), "Autocomplete UI error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // Confirm button - return to host
        confirmButton.setOnClickListener {
            val addr = selectedAddress
            val lat = selectedLat
            val lng = selectedLng
            if (addr != null && lat != null && lng != null) {
                listener.onLocationSelected(addr, lat, lng)
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Please pick a location from search first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Show pin helper — decodes drawable to Bitmap and creates annotation
    private fun showPinAt(point: Point) {
        val manager = pointAnnotationManager ?: return
        manager.deleteAll()
        currentAnnotation = null

        val iconBitmap: Bitmap? = try {
            BitmapFactory.decodeResource(requireContext().resources, R.drawable.ic_location_pin)
        } catch (e: Exception) {
            Log.w("LocationPicker", "Failed to decode bitmap resource: ${e.message}")
            null
        }

        val options = PointAnnotationOptions().withPoint(point).withIconImage("")

        // If we have a bitmap, scale to a reasonable pixel size and set it as the icon
        iconBitmap?.let { bmp ->
            options.withIconImage(iconBitmap)
        }

        try {
            currentAnnotation = manager.create(options)
        } catch (e: Exception) {
            // fallback: ignore if annotation create fails; log if needed
        }
    }

    private fun hideKeyboardAndClearFocus() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchBar.windowToken, 0)
        searchBar.clearFocus()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }
}
