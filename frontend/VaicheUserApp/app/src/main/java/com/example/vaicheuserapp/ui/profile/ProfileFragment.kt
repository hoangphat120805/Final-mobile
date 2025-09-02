package com.example.vaicheuserapp.ui.profile

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.example.vaicheuserapp.LoginActivity
import com.example.vaicheuserapp.MainActivity
import com.example.vaicheuserapp.R
import com.example.vaicheuserapp.data.model.UserPublic
import com.example.vaicheuserapp.data.model.UserUpdateRequest
import com.example.vaicheuserapp.data.network.RetrofitClient
import com.example.vaicheuserapp.databinding.FragmentProfileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import coil.request.CachePolicy
import coil.memory.MemoryCache
import com.example.vaicheuserapp.ResetPasswordActivity

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var currentUser: UserPublic? = null
    private var isEditing: Boolean = false
    private var uploadedAvtUrl: String? = null

    // Define a default avatar URL - CRITICAL: CHANGE THIS TO YOUR BACKEND'S REAL DEFAULT AVATAR URL
    private val DEFAULT_AVATAR_URL = "https://i.ibb.co/5xt2NvW0/453178253-471506465671661-2781666950760530985-n.png" // Example default

    // ActivityResultLaunchers for gallery pick and permissions
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    // REMOVED: private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                pickImageFromGallery() // Directly pick from gallery if permission granted
            } else {
                Toast.makeText(requireContext(), "Permission denied to access gallery", Toast.LENGTH_SHORT).show()
            }
        }

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    binding.ivProfilePicture.load(uri) { // Display locally
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.bg_image_placeholder)
                        error(R.drawable.bg_image_error)
                    }
                    uploadAvatar(uri) // Trigger upload here!
                }
            }
        }
        // REMOVED: takePictureLauncher initialization
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("ProfileFragment", "onViewCreated called.")

        ViewCompat.setOnApplyWindowInsetsListener(binding.viewProfileHeaderBackground) { v, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBarsInsets.top)
            insets
        }

        setupListeners()
        fetchUserProfile()
    }

    override fun onResume() {
        super.onResume()
        fetchUserProfile()
    }

    private fun setupListeners() {
        binding.btnEditProfile.setOnClickListener { toggleEditMode(true) }
        binding.btnSaveProfile.setOnClickListener { saveUserProfile() }
        binding.btnResetPassword.setOnClickListener { navigateToResetPassword() }
        binding.btnLogout.setOnClickListener { logoutUser() }

        binding.etBirthDateEdit.setOnClickListener { showDatePicker() }
        binding.etBirthDateEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showDatePicker()
        }

        binding.ivCameraIcon.setOnClickListener {
            // Only request READ_MEDIA_IMAGES (or READ_EXTERNAL_STORAGE)
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
                pickImageFromGallery() // Directly pick from gallery if permission granted
            } else {
                requestPermissionLauncher.launch(permission) // Request permission
            }
        }
    }

    // REMOVED: showImageSourceSelection()
    // REMOVED: takePhotoWithCamera()
    // REMOVED: createImageFile()

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // --- NEW HELPER FUNCTION: Convert Uri to a temporary File for upload ---
    private suspend fun uriToFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        val fileExtension = context.contentResolver.getType(uri)?.substringAfter("/") ?: "jpg" // Get extension or default
        val fileName = "avatar_${System.currentTimeMillis()}.$fileExtension"
        val tempFile = File(context.cacheDir, fileName)

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            outputStream = FileOutputStream(tempFile)
            inputStream?.copyTo(outputStream)
            return@withContext tempFile
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error converting Uri to File: ${e.message}", e)
            return@withContext null
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }
    // --- END NEW HELPER FUNCTION ---

    private fun fetchUserProfile(skipCache: Boolean = false) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getUserMe()
                if (response.isSuccessful && response.body() != null) {
                    currentUser = response.body()
                    populateProfileData(currentUser!!, skipCache) // Pass skipCache to populateUi
                    toggleEditMode(false)
                } else {
                    Log.e("ProfileFragment", "Failed to fetch user profile: ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
                    if (response.code() == 401) logoutUser()
                    if (currentUser == null) {
                        currentUser = UserPublic(
                            id = "", phoneNumber = "", role = "",
                            fullName = "Guest", gender = "Unknown", birthDate = "N/A", email = "N/A",
                            avtUrl = DEFAULT_AVATAR_URL
                        )
                        populateProfileData(currentUser!!)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error fetching user profile: ${e.message}", e)
                Toast.makeText(requireContext(), "An error occurred: ${e.message}", Toast.LENGTH_SHORT).show()
                if (currentUser == null) {
                    currentUser = UserPublic(
                        id = "", phoneNumber = "", role = "",
                        fullName = "Guest", gender = "Unknown", birthDate = "N/A", email = "N/A",
                        avtUrl = DEFAULT_AVATAR_URL
                    )
                    populateProfileData(currentUser!!)
                }
            }
        }
    }

    private fun populateProfileData(user: UserPublic, skipCache: Boolean = false) {
        binding.tvWelcomeName.text = "Welcome, ${user.fullName ?: user.phoneNumber}"
        binding.tvPhoneNumber.text = user.phoneNumber

        binding.tvFullNameView.text = user.fullName ?: "N/A"
        binding.etFullNameEdit.setText(user.fullName)

        binding.tvEmailView.text = user.email ?: "N/A"
        binding.etEmailEdit.setText(user.email)

        binding.tvGenderView.text = user.gender ?: "Unknown"
        binding.etGenderEdit.setText(user.gender)

        binding.tvBirthDateView.text = user.birthDate ?: "N/A"
        binding.etBirthDateEdit.setText(user.birthDate)

        binding.ivProfilePicture.load(user.avtUrl, RetrofitClient.imageLoader) {
            crossfade(true)
            transformations(CircleCropTransformation())
            placeholder(R.drawable.bg_image_placeholder)
            error(R.drawable.bg_image_error)

            // --- CRITICAL FIX: Skip cache if flag is true ---
            if (skipCache) {
                memoryCachePolicy(CachePolicy.DISABLED)
                diskCachePolicy(CachePolicy.DISABLED)
                networkCachePolicy(CachePolicy.ENABLED) // Ensure network is used
            }
        }
    }

    private fun toggleEditMode(enableEdit: Boolean) {
        isEditing = enableEdit
        val visibilityView = if (enableEdit) View.GONE else View.VISIBLE
        val visibilityEdit = if (enableEdit) View.VISIBLE else View.GONE

        binding.tvFullNameView.visibility = visibilityView
        binding.etFullNameEdit.visibility = visibilityEdit
        binding.etFullNameEdit.isEnabled = enableEdit

        binding.tvEmailView.visibility = visibilityView
        binding.etEmailEdit.visibility = visibilityEdit
        binding.etEmailEdit.isEnabled = enableEdit

        binding.tvGenderView.visibility = visibilityView
        binding.etGenderEdit.visibility = visibilityEdit
        binding.etGenderEdit.isEnabled = enableEdit

        binding.tvBirthDateView.visibility = visibilityView
        binding.etBirthDateEdit.visibility = visibilityEdit
        binding.etBirthDateEdit.isEnabled = enableEdit

        binding.btnEditProfile.visibility = visibilityView
        binding.btnResetPassword.visibility = visibilityView
        binding.btnLogout.visibility = visibilityView

        binding.btnSaveProfile.visibility = visibilityEdit
        binding.ivCameraIcon.visibility = visibilityEdit
    }

    private fun uploadAvatar(imageUri: Uri) {
        binding.pbUploadAvatar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val file = uriToFile(requireContext(), imageUri)
            if (file == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to process image for upload", Toast.LENGTH_LONG).show()
                    binding.pbUploadAvatar.visibility = View.GONE
                }
                return@launch
            }

            withContext(Dispatchers.IO) {
                try {
                    val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                    val response = RetrofitClient.instance.uploadAvatar(body)
                    if (response.isSuccessful) {
                        Log.d("ProfileFragment", "Avatar uploaded. Server message: ${response.body()?.message}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), response.body()?.message ?: "Avatar uploaded successfully!", Toast.LENGTH_SHORT).show()

                            // --- CRITICAL FIX: Invalidate Coil's cache AND re-fetch user profile ---
                            // 1. Invalidate cache for the specific avatar URL
                            //    (Assuming fetchUserProfile will get the *new* avtUrl)
                            currentUser?.avtUrl?.let { oldAvtUrl ->
                                RetrofitClient.imageLoader.memoryCache?.remove(MemoryCache.Key(oldAvtUrl))
                                RetrofitClient.imageLoader.diskCache?.remove(oldAvtUrl)
                            }
                            // 2. Then re-fetch, which will cause populateProfileData to reload with cache policy disabled
                            fetchUserProfile(skipCache = true) // Pass a flag to skip cache on re-fetch
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("ProfileFragment", "Avatar upload failed: $errorBody")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Avatar upload failed: ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "Error during avatar upload: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error uploading avatar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        binding.pbUploadAvatar.visibility = View.GONE
                        file.delete()
                    }
                }
            }
        }
    }

    // --- Save User Profile Function (MODIFIED) ---
    private fun saveUserProfile() {
        val updatedFullName = binding.etFullNameEdit.text.toString().trim()
        val updatedEmail = binding.etEmailEdit.text.toString().trim()
        val updatedGender = binding.etGenderEdit.text.toString().trim()
        val updatedBirthDate = binding.etBirthDateEdit.text.toString().trim()

        // CRITICAL FIX: Determine the avtUrl to send.
        // It must NOT be null.
        // Logic:
        // 1. If an image was just successfully uploaded and its URL is in uploadedAvtUrl.
        // 2. Otherwise, use the avtUrl that we currently have for the user (from the last fetchUserProfile).
        // 3. If even that is null (shouldn't happen if backend always sends one), use a default fallback.
        val finalAvtUrlToSave = uploadedAvtUrl ?: currentUser?.avtUrl ?: DEFAULT_AVATAR_URL

        val request = UserUpdateRequest(
            fullName = updatedFullName.ifEmpty { null },
            email = updatedEmail.ifEmpty { null },
            gender = updatedGender.ifEmpty { null },
            birthDate = updatedBirthDate.ifEmpty { null },
            phoneNumber = null, // Phone number is not updatable via PATCH /user/me
            avtUrl = finalAvtUrlToSave // <-- NOW INCLUDED AND GUARANTEED NON-NULL
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateUserMe(request)
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(requireContext(), "Profile updated!", Toast.LENGTH_SHORT).show()
                    currentUser = response.body() // Get updated user data (including the avtUrl we just sent)
                    populateProfileData(currentUser!!) // Refresh UI
                    toggleEditMode(false)
                    uploadedAvtUrl = null // Clear this after a successful save
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("ProfileFragment", "Failed to update profile: ${response.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Update failed: ${errorBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error updating profile: ${e.message}", e)
                Toast.makeText(requireContext(), "An error occurred: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDayOfMonth)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                binding.etBirthDateEdit.setText(dateFormat.format(selectedCalendar.time))
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun navigateToResetPassword() {
        val intent = Intent(requireContext(), ResetPasswordActivity::class.java)
        startActivity(intent)
    }

    private fun logoutUser() {
        val sharedPreferences = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().remove("auth_token").apply()
        Toast.makeText(requireContext(), "Logged out!", Toast.LENGTH_SHORT).show()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish() // Finish the host activity
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}