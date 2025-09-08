package com.example.vaiche_driver.ui

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
import com.example.vaiche_driver.R
import com.example.vaiche_driver.viewmodel.Event
import com.example.vaiche_driver.viewmodel.SettingsViewModel
import com.example.vaiche_driver.viewmodel.SettingsViewModelFactory
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
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

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadAvatarFromUri(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progress = view.findViewById(R.id.progress_settings)

        view.findViewById<MaterialCardView>(R.id.card_update_password).setOnClickListener {
            showUpdatePasswordDialog()
        }
        view.findViewById<MaterialCardView>(R.id.card_edit_avatar).setOnClickListener {
            pickImage.launch("image/*")
        }
        view.findViewById<MaterialCardView>(R.id.card_edit_profile).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, EditProfileFragment())
                .addToBackStack(null)
                .commit()
        }
        view.findViewById<MaterialCardView>(R.id.card_logout).setOnClickListener {
            viewModel.logout()
        }

        observeVM()
    }

    private fun observeVM() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progress?.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.toastMessage.observe(viewLifecycleOwner) { ev: Event<String> ->
            ev.getContentIfNotHandled()?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
        }
        viewModel.loggedOut.observe(viewLifecycleOwner) { done ->
            if (done == true) {
                // TODO: điều hướng về màn hình đăng nhập
                requireActivity().finish()
            }
        }
    }

    private fun showUpdatePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update_password, null)
        val edtOld = dialogView.findViewById<TextInputEditText>(R.id.edt_old_password)
        val edtNew = dialogView.findViewById<TextInputEditText>(R.id.edt_new_password)
        val edtConfirm = dialogView.findViewById<TextInputEditText>(R.id.edt_confirm_password)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update password")
            .setView(dialogView)
            .setPositiveButton("Update") { d, _ ->
                val oldP = edtOld.text?.toString().orEmpty()
                val newP = edtNew.text?.toString().orEmpty()
                val confirm = edtConfirm.text?.toString().orEmpty()
                when {
                    oldP.isBlank() || newP.isBlank() -> toast("Please fill all fields")
                    newP != confirm -> toast("Confirm mismatch")
                    else -> viewModel.updatePassword(oldP, newP)
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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

    private fun queryDisplayName(uri: Uri): String? {
        val c = requireContext().contentResolver.query(uri, null, null, null, null) ?: return null
        c.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) return it.getString(idx)
        }
        return null
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
