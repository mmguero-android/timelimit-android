/*
 * TimeLimit Copyright <C> 2019 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.ui.manage.parent.password.restore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.databinding.RestoreParentPasswordFragmentBinding
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.ui.authentication.AuthenticateByMailFragment
import io.timelimit.android.ui.authentication.AuthenticateByMailFragmentListener
import io.timelimit.android.ui.main.FragmentWithCustomTitle

class RestoreParentPasswordFragment : Fragment(), AuthenticateByMailFragmentListener, FragmentWithCustomTitle {
    companion object {
        private const val PAGE_CHOSE_PASSWORD = 0
        private const val PAGE_AUTHENTICATE = 1
        private const val PAGE_WORKING = 2
        private const val PAGE_AUTH_INVALID = 3
    }

    val params: RestoreParentPasswordFragmentArgs by lazy {
        RestoreParentPasswordFragmentArgs.fromBundle(arguments!!)
    }

    val model: RestoreParentPasswordViewModel by lazy {
        ViewModelProviders.of(this).get(RestoreParentPasswordViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = RestoreParentPasswordFragmentBinding.inflate(inflater, container, false)
        val navigation = Navigation.findNavController(container!!)

        model.status.observe(this, Observer {
            status ->

            when (status!!) {
                RestoreParentPasswordStatus.WaitForAuthentication -> {
                    binding.flipper.displayedChild = PAGE_AUTHENTICATE
                }
                RestoreParentPasswordStatus.Working -> {
                    binding.flipper.displayedChild = PAGE_WORKING
                }
                RestoreParentPasswordStatus.ErrorCanNotRecover -> {
                    binding.flipper.displayedChild = PAGE_AUTH_INVALID
                }
                RestoreParentPasswordStatus.NetworkError -> {
                    Toast.makeText(context!!, R.string.error_network, Toast.LENGTH_SHORT).show()

                    navigation.popBackStack()

                    null
                }
                RestoreParentPasswordStatus.Done -> {
                    Toast.makeText(context!!, R.string.manage_parent_change_password_toast_success, Toast.LENGTH_SHORT).show()

                    navigation.popBackStack()

                    null
                }
                RestoreParentPasswordStatus.WaitForNewPassword -> {
                    binding.flipper.displayedChild = PAGE_CHOSE_PASSWORD
                }
            }.let {  }
        })

        binding.newPassword.passwordOk.observe(this, Observer {
            binding.changePasswordBtn.isEnabled = it!!
        })

        binding.changePasswordBtn.setOnClickListener {
            model.changePassword(binding.newPassword.password.value!!)
        }

        return binding.root
    }

    override fun onLoginSucceeded(mailAuthToken: String) {
        model.setParams(mailAuthToken, params.parentId)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.mail_auth_container, AuthenticateByMailFragment.newInstance(recoveryUserId = params.parentId))
                    .commit()
        }
    }

    override fun getCustomTitle(): LiveData<String?> = liveDataFromValue(getString(R.string.restore_parent_password_title))
}
