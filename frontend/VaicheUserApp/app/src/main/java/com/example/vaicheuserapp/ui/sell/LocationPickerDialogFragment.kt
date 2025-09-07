package com.example.vaicheuserapp.ui.sell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
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
import android.util.Log
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

class LocationPickerDialog : DialogFragment() {

    interface OnLocationSelectedListener {
        fun onLocationSelected(address: String, latitude: Double, longitude: Double)
    }

    private lateinit var mapView: MapView
    private lateinit var searchBar: EditText
    private lateinit var searchResultsView: SearchResultsView
    private lateinit var placeAutocomplete: PlaceAutocomplete

    private var selectedAddress: String? = null
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null

    // Annotation manager / current annotation and pending point support
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var currentAnnotation: PointAnnotation? = null
    private var pendingPointToShow: Point? = null

    private var suppressSearch: Boolean = false

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
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS) { style ->
            // Create annotation manager like in your sample:
            val annotationApi = mapView.annotations
            try {
                pointAnnotationManager = annotationApi.createPointAnnotationManager(AnnotationConfig())
            } catch (e: Exception) {
                Log.e("LocationPicker", "createPointAnnotationManager failed: ${e.message}")
                pointAnnotationManager = null
            }

            // if a selection happened before manager ready, handle it
            pendingPointToShow?.let { pt ->
                showPinAt(pt)
                pendingPointToShow = null
            }
        }

        // initial camera to HCMC and restrict bounds to Vietnam
        val hcm = Point.fromLngLat(106.7009, 10.7769)
        mapboxMap.setCamera(CameraOptions.Builder().center(hcm).zoom(11.0).build())

        val sw = Point.fromLngLat(102.0, 8.0)
        val ne = Point.fromLngLat(110.6, 23.5)
        val bounds = CoordinateBounds(sw, ne)
        try {
            mapboxMap.setBounds(
                com.mapbox.maps.CameraBoundsOptions.Builder()
                    .bounds(bounds)
                    .minZoom(5.0)
                    .maxZoom(18.0)
                    .build()
            )
        } catch (e: Exception) {
            Log.w("LocationPicker", "setBounds failed: ${e.message}")
        }

        // Initialize SearchResultsView
        val commonConfig = CommonSearchViewConfiguration()
        val resultsConfig = SearchResultsView.Configuration(commonConfig)
        searchResultsView.initialize(resultsConfig)

        // Create adapter AFTER results view is initialized
        val adapter = PlaceAutocompleteUiAdapter(view = searchResultsView, placeAutocomplete = placeAutocomplete)

        // Make searchBar friendly to autocorrect/suggestions, single-line
        searchBar.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
        searchBar.isSingleLine = true
        searchBar.isFocusable = true
        searchBar.isFocusableInTouchMode = true

        // Re-enable user-typing search when user touches/focuses the field
        searchBar.setOnTouchListener { _, _ ->
            suppressSearch = false
            false
        }
        searchBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) suppressSearch = false
        }

        // Text watcher -> adapter.search (suspend)
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (suppressSearch) return

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

        // When a suggestion is selected in the UI list
        adapter.addSearchListener(object : PlaceAutocompleteUiAdapter.SearchListener {
            override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {
                lifecycleScope.launch {
                    try {
                        val selectionResponse = placeAutocomplete.select(suggestion)
                        selectionResponse.onValue { result ->
                            val point: Point? = result.coordinate
                            if (point != null) {
                                // Move camera to selection
                                mapboxMap.setCamera(CameraOptions.Builder().center(point).zoom(14.0).build())

                                // formatted address (String?)
                                val formatted = result.address?.formattedAddress ?: suggestion.name

                                // Show pin (will use bitmapFromDrawableRes -> decode PNG)
                                showPinAt(point)

                                // set search text programmatically and suppress next automatic search
                                suppressSearch = true
                                searchBar.setText(formatted)
                                searchBar.setSelection(formatted.length)

                                // store choice
                                selectedAddress = formatted
                                selectedLat = point.latitude()
                                selectedLng = point.longitude()

                                // hide suggestions, clear focus & keyboard
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

    // showPinAt using bitmapFromDrawableRes (PNG resource)
    private fun showPinAt(point: Point) {
        val manager = pointAnnotationManager
        if (manager == null) {
            // manager not ready yet — queue the point
            pendingPointToShow = point
            Log.d("LocationPicker", "Annotation manager not ready yet — queuing pin")
            return
        }

        // clear previous
        try {
            manager.deleteAll()
            currentAnnotation = null
        } catch (e: Exception) {
            Log.w("LocationPicker", "Failed to clear annotations: ${e.message}")
        }

        // Decode the PNG bitmap resource (make sure R.drawable.ic_location_pin exists)
        val iconBitmap: Bitmap? = try {
            bitmapFromDrawableRes(requireContext(), R.drawable.ic_location_pin)
        } catch (e: Exception) {
            Log.w("LocationPicker", "Failed to decode bitmap resource: ${e.message}")
            null
        }

        val options = PointAnnotationOptions().withPoint(point)
        iconBitmap?.let { bmp ->
            // scale to reasonable size (adjust 96 if you want it bigger/smaller)
            val scaled = Bitmap.createScaledBitmap(bmp, 96, 96, true)
            options.withIconImage(scaled)
        }

        try {
            currentAnnotation = manager.create(options)
            Log.d("LocationPicker", "Annotation created at: ${point.latitude()}, ${point.longitude()}")
        } catch (e: Exception) {
            Log.e("LocationPicker", "Failed to create annotation: ${e.message}")
        }
    }

    private fun hideKeyboardAndClearFocus() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchBar.windowToken, 0)
        searchBar.clearFocus()
    }

    // helper copied/adapted from your sample: decode a drawable resource to Bitmap
    private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int): Bitmap? {
        return convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))
    }

    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) return null
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            // fallback size if intrinsic <= 0
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
            val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
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
