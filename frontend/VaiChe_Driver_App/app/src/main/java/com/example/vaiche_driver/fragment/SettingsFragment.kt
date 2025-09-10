package com.example.vaiche_driver.fragment

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.vaiche_driver.MainActivity
import com.example.vaiche_driver.R
import com.example.vaiche_driver.fragment.EditProfileFragment
import com.example.vaiche_driver.util.forceQuitAndReopenApp
import com.example.vaiche_driver.viewmodel.Event
import com.example.vaiche_driver.viewmodel.SettingsViewModel
import com.example.vaiche_driver.viewmodel.SettingsViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireContext())
    }

    private var progress: ProgressBar? = null

    // Gallery picker
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadAvatarFromUri(it) }
    }

    // Camera capture (bitmap)
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { uploadAvatarFromBitmap(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progress = view.findViewById(R.id.progress_settings)

        // Toolbar back -> về ProfileFragment (pop stack)
        view.findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Update password -> mở Fragment
        view.findViewById<MaterialCardView>(R.id.card_update_password).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, UpdatePasswordFragment())
                .addToBackStack(null)
                .commit()
        }

        // Edit avatar: chọn Camera hoặc Gallery
        view.findViewById<MaterialCardView>(R.id.card_edit_avatar).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Change Avatar")
                .setItems(arrayOf("Take Photo", "Choose from Gallery")) { _, which ->
                    when (which) {
                        0 -> cameraLauncher.launch(null)
                        1 -> pickImage.launch("image/*")
                    }
                }.show()
        }

        // Edit profile -> sang EditProfileFragment
        view.findViewById<MaterialCardView>(R.id.card_edit_profile).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, EditProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        // Logout
        view.findViewById<MaterialCardView>(R.id.card_logout).setOnClickListener {
            viewModel.logout(requireContext())
        }

        observeVM()
    }

    private fun observeVM() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progress?.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { ev: Event<String> ->
            ev.getContentIfNotHandled()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }

        // Nếu muốn Settings tự xử lý điều hướng khi update password từ UpdatePasswordFragment xong:
        // (Chỉ cần nếu bạn phát cùng Event từ VM và muốn pop thêm)
        viewModel.passwordUpdated.observe(viewLifecycleOwner) { ev ->
            ev.getContentIfNotHandled()?.let {
                // về Profile: Settings đang trên backstack, nên pop 1 lần là về Profile
                parentFragmentManager.popBackStack() // rời UpdatePassword -> về Settings
                parentFragmentManager.popBackStack() // rời Settings -> về Profile
            }
        }

        viewModel.loggedOut.observe(viewLifecycleOwner) { done ->
            if (done == true) {
                // TRƯỚC ĐÂY: (activity as? MainActivity)?.logoutToLogin()
                // GIỜ: thoát hẳn và mở lại Login
                requireActivity().forceQuitAndReopenApp()
            }
        }

    }

    // ===== Avatar helpers =====

    private fun uploadAvatarFromUri(uri: Uri) {
        val fileName = queryDisplayName(uri) ?: "avatar.jpg"
        val cacheFile = File(requireContext().cacheDir, fileName)
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
        }
        val reqBody = cacheFile.asRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", cacheFile.name, reqBody)
        viewModel.uploadAvatar(part)
    }

    private fun uploadAvatarFromBitmap(bitmap: Bitmap) {
        val file = File(requireContext().cacheDir, "avatar_camera.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        val reqBody = file.asRequestBody("image/*".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, reqBody)
        viewModel.uploadAvatar(part)
    }

    private fun queryDisplayName(uri: Uri): String? {
        val c = requireContext().contentResolver.query(uri, null, null, null, null) ?: return null
        c.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) return it.getString(idx)
        }
        return null
    }
}
