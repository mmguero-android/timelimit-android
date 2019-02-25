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
package io.timelimit.android.ui.authentication

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentAuthenticateByMailBinding
import io.timelimit.android.extensions.setOnEnterListenr
import io.timelimit.android.flavors.GoogleSignInUtil
import io.timelimit.android.livedata.map
import io.timelimit.android.ui.MainActivity

class AuthenticateByMailFragment : Fragment() {
    companion object {
        private const val REQUEST_SIGN_IN_WITH_GOOGLE = 1
        private const val EXTRA_HIDE_SIGN_IN_WITH_GOOGLE_BUTTON = "hsiwgb"

        fun newInstance(hideSignInWithGoogleButton: Boolean) = AuthenticateByMailFragment().apply {
            arguments = Bundle().apply {
                putBoolean(EXTRA_HIDE_SIGN_IN_WITH_GOOGLE_BUTTON, hideSignInWithGoogleButton)
            }
        }
    }

    private val listener: AuthenticateByMailFragmentListener by lazy { parentFragment as AuthenticateByMailFragmentListener }
    private val googleAuthUtil: GoogleSignInUtil by lazy { (activity as MainActivity).googleSignInUtil }
    private val model: AuthenticateByMailModel by lazy { ViewModelProviders.of(this).get(AuthenticateByMailModel::class.java) }
    private val hideSignInWithGoogleButton: Boolean by lazy {
        arguments?.getBoolean(EXTRA_HIDE_SIGN_IN_WITH_GOOGLE_BUTTON, false) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentAuthenticateByMailBinding.inflate(layoutInflater, container, false)

        model.usingDefaultServer.observe(this, Observer {
            binding.showSignInWithGoogleButton = it && (!hideSignInWithGoogleButton)
        })

        binding.signInWithGoogleButton.setOnClickListener {
            startActivityForResult(googleAuthUtil.getSignInIntent(), REQUEST_SIGN_IN_WITH_GOOGLE)
        }

        binding.mailEdit.setOnEnterListenr {
            val mail = binding.mailEdit.text.toString()

            if (mail.isNotBlank()) {
                model.sendAuthMessage(mail)
            }
        }

        binding.codeEdit.setOnEnterListenr {
            val code = binding.codeEdit.text.toString()

            if (code.isNotBlank()) {
                model.confirmCode(code)

                binding.codeEdit.setText("")
            }
        }

        model.mailAddressToWhichCodeWasSent.observe(this, Observer {
            binding.mailAddressToWhichCodeWasSent = it
        })

        model.isBusy.map {
            if (it) {
                1
            } else {
                0
            }
        }.observe(this, Observer {
            binding.switcher.displayedChild = it!!
        })

        model.mailAuthToken.observe(this, Observer {
            if (it != null) {
                listener.onLoginSucceeded(it)
            }
        })

        model.errorMessage.observe(this, Observer {
            if (it != null) {
                Snackbar.make(
                        binding.root,
                        when(it) {
                            ErrorMessage.NetworkProblem -> R.string.error_network
                            ErrorMessage.ServerRejection -> R.string.error_server_rejected
                            ErrorMessage.WrongCode -> R.string.authenticate_by_mail_snackbar_wrong_code
                        },
                        Snackbar.LENGTH_SHORT
                ).show()

                model.errorMessage.value = null
            }
        })

        return binding.root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_SIGN_IN_WITH_GOOGLE) {
            val googleAuthToken = googleAuthUtil.processActivityResult(data)

            if (googleAuthToken != null) {
                model.handleGoogleAuthToken(googleAuthToken)
            }
        }
    }
}

interface AuthenticateByMailFragmentListener {
    fun onLoginSucceeded(mailAuthToken: String)
}
