package com.example.vaiche_driver.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.vaiche_driver.R
import com.example.vaiche_driver.viewmodel.Event
import com.example.vaiche_driver.viewmodel.SettingsViewModel
import com.example.vaiche_driver.viewmodel.SettingsViewModelFactory
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class UpdatePasswordFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(requireContext())
    }

    private var progress: ProgressBar? = null
    private var etOld: TextInputEditText? = null
    private var etNew: TextInputEditText? = null
    private var etConfirm: TextInputEditText? = null
    private var btnUpdate: MaterialButton? = null
    private var toolbar: MaterialToolbar? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_update_password, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        progress = view.findViewById(R.id.progress_bar_reset)
        toolbar = view.findViewById(R.id.toolbar_update_password)
        etOld = view.findViewById(R.id.et_old_password)
        etNew = view.findViewById(R.id.et_new_password)
        etConfirm = view.findViewById(R.id.et_confirm_password)
        btnUpdate = view.findViewById(R.id.btn_reset_password_final)

        // Back trên toolbar -> quay về Settings
        toolbar?.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Bấm Update
        btnUpdate?.setOnClickListener {
            val oldP = etOld?.text?.toString().orEmpty()
            val newP = etNew?.text?.toString().orEmpty()
            val confirm = etConfirm?.text?.toString().orEmpty()

            when {
                oldP.isBlank() || newP.isBlank() || confirm.isBlank() -> toast("Please fill all fields")
                newP != confirm -> toast("Confirm mismatch")
                newP.length < 3 -> toast("Password must be at least 3 characters")
                else -> viewModel.updatePassword(oldP, newP)
            }
        }

        observeVM()
    }

    private fun observeVM() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progress?.visibility = if (loading) View.VISIBLE else View.GONE
            btnUpdate?.isEnabled = !loading
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { ev: Event<String> ->
            ev.getContentIfNotHandled()?.let { toast(it) }
        }

        // Cập nhật mật khẩu thành công:
        // pop về Settings, rồi pop tiếp về Profile (nếu flow là Profile -> Settings -> UpdatePassword)
        viewModel.passwordUpdated.observe(viewLifecycleOwner) { ev ->
            ev.getContentIfNotHandled()?.let {
                // Về Settings
                parentFragmentManager.popBackStack()
                // Về Profile
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
