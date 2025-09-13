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
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.example.vaiche_driver.MainActivity
import com.example.vaiche_driver.R
import com.example.vaiche_driver.viewmodel.Event
import com.example.vaiche_driver.viewmodel.SettingsViewModel
import com.example.vaiche_driver.viewmodel.SettingsViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

// để fetch avatarUrl mới
import com.example.vaiche_driver.data.repository.ProfileRepository
import com.example.vaiche_driver.data.network.RetrofitClient

class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireContext())
    }

    private var progress: ProgressBar? = null
    private var logoutHandled = false // tránh double-trigger

    // Gallery picker
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadAvatarFromUri(it) }
    }

    // Camera capture (bitmap-preview)
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

        // Update password
        view.findViewById<MaterialCardView>(R.id.card_update_password).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, UpdatePasswordFragment())
                .addToBackStack(null)
                .commit()
        }

        // Edit avatar
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

        // Edit profile
        view.findViewById<MaterialCardView>(R.id.card_edit_profile).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, EditProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        // Logout
        view.findViewById<MaterialCardView>(R.id.card_logout).setOnClickListener {
            (requireActivity() as MainActivity).logoutToLogin()
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

        // Đổi mật khẩu xong -> pop 2 lần để về Profile
        viewModel.passwordUpdated.observe(viewLifecycleOwner) { ev ->
            ev.getContentIfNotHandled()?.let {
                parentFragmentManager.popBackStack()
                parentFragmentManager.popBackStack()
            }
        }

        // Avatar đổi xong: chờ URL reachable -> preload -> báo Dashboard -> refresh Profile -> back
        viewModel.avatarUpdated.observe(viewLifecycleOwner) { ev ->
            ev.getContentIfNotHandled()?.let { signatureKey ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val newUrl = fetchLatestAvatarUrlOrNull()
                    val ok = if (!newUrl.isNullOrBlank()) waitUntilReachable(newUrl) else false

                    if (ok && !newUrl.isNullOrBlank()) {
                        Glide.with(requireContext())
                            .asBitmap()
                            .load(newUrl)
                            .signature(ObjectKey(signatureKey))
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .preload(128, 128)
                    }

                    // Báo Dashboard giữ nguyên
                    if (!newUrl.isNullOrBlank()) {
                        setFragmentResult("collector_avatar_updated", bundleOf("url" to newUrl))
                    }

                    // Ép Profile refresh (Profile sẽ tự load lại mỗi lần vào, nhưng nếu đang mở thì vẫn làm tươi)
                    findProfileFragment()?.refreshNow(signatureKey)

                    parentFragmentManager.popBackStack()
                }
            }
        }

        // Chỉ thay đổi info profile
        viewModel.profileUpdated.observe(viewLifecycleOwner) { ev ->
            ev.getContentIfNotHandled()?.let {
                findProfileFragment()?.refreshNow(null)
                parentFragmentManager.popBackStack()
            }
        }

    }

    /** Tìm instance ProfileFragment dưới back stack (nếu có) */
    private fun findProfileFragment(): ProfileFragment? {
        parentFragmentManager.fragments.forEach { f ->
            if (f is ProfileFragment) return f
        }
        return null
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

    // ====== lấy avatarUrl mới + (để preload nhanh) ======
    private suspend fun fetchLatestAvatarUrlOrNull(): String? = withContext(Dispatchers.IO) {
        try {
            val repo = ProfileRepository { RetrofitClient.instance }
            repo.getUserProfile().getOrNull()?.avatarUrl
        } catch (_: Throwable) { null }
    }

    // ====== chờ URL Cloudinary reachable (HEAD rồi fallback GET nhẹ) ======
    private suspend fun waitUntilReachable(url: String, maxTries: Int = 6): Boolean =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder().build()
            repeat(maxTries) { attempt ->
                try {
                    val headReq = Request.Builder().url(url).head().build()
                    client.newCall(headReq).execute().use { resp ->
                        if (resp.isSuccessful) return@withContext true
                        if (resp.code == 405) {
                            val getReq = Request.Builder()
                                .url(url)
                                .addHeader("Range", "bytes=0-0")
                                .get().build()
                            client.newCall(getReq).execute().use { getResp ->
                                if (getResp.isSuccessful || getResp.code == 206) return@withContext true
                            }
                        }
                    }
                } catch (_: Throwable) { }
                try { delay(300L * (attempt + 1)) } catch (_: Throwable) { }
            }
            false
        }
}
