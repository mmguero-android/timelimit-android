/*
 * TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
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
import io.timelimit.android.util.MailValidation

class AuthenticateByMailFragment : Fragment() {
    companion object {
        private const val RECOVERY_USER_ID = "userId"

        fun newInstance(recoveryUserId: String) = AuthenticateByMailFragment().apply {
            arguments = Bundle().apply {
                putString(RECOVERY_USER_ID, recoveryUserId)
            }
        }
    }

    private val listener: AuthenticateByMailFragmentListener by lazy { parentFragment as AuthenticateByMailFragmentListener }
    val model: AuthenticateByMailModel by lazy { ViewModelProviders.of(this).get(AuthenticateByMailModel::class.java) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentAuthenticateByMailBinding.inflate(layoutInflater, container, false)

        model.recoveryUserId.value = arguments?.getString(RECOVERY_USER_ID)

        binding.step1.mailEdit.setOnEnterListenr {
            val mail = binding.step1.mailEdit.text.toString()

            if (!MailValidation.seemsMailAddressValid(mail)) {
                Snackbar.make(binding.root, R.string.authenticate_by_mail_snackbar_invalid_address, Snackbar.LENGTH_SHORT).show()
                return@setOnEnterListenr
            }

            val domain = MailValidation.getDomain(mail)
            val suggestedDomain = MailValidation.suggestAlternativeDomain(domain)

            if (!MailValidation.seemsDomainValid(domain)) {
                if (suggestedDomain == null) {
                    Snackbar.make(binding.root, R.string.authenticate_by_mail_snackbar_invalid_address, Snackbar.LENGTH_SHORT).show()
                } else {
                    val mailWithoutDomain = mail.substring(0, mail.length - domain.length)
                    val mailWithSuggestedDomain = mailWithoutDomain + suggestedDomain

                    binding.step1.mailEdit.setText(mailWithSuggestedDomain)
                    Snackbar.make(binding.root, R.string.authenticate_by_mail_snackbar_invalid_address_suggest, Snackbar.LENGTH_SHORT).show()
                }

                return@setOnEnterListenr
            } else {
                if (suggestedDomain != null) {
                    val mailWithoutDomain = mail.substring(0, mail.length - domain.length)
                    val mailWithSuggestedDomain = mailWithoutDomain + suggestedDomain

                    SelectMailDialogFragment.newInstance(
                            options = listOf(mailWithSuggestedDomain, mail),
                            target = this
                    ).show(fragmentManager!!)
                } else {
                    model.sendAuthMessage(mail)
                }
            }
        }

        binding.step2.codeEdit.setOnEnterListenr {
            val code = binding.step2.codeEdit.text.toString()

            if (code.isNotBlank()) {
                model.confirmCode(code)

                binding.step2.codeEdit.setText("")
            }
        }

        binding.step1B.goButton.setOnClickListener {
            model.sendAuthMessageToForcedMailAddress()
        }

        model.mailAddressToWhichCodeWasSent.observe(this, Observer {
            binding.step2.mailAddressToWhichCodeWasSent = it
        })

        model.screenToShow.observe(this, Observer {
            binding.flipper.displayedChild = when (it!!) {
                ScreenToShow.EnterMailAddress -> 0
                ScreenToShow.EnterReceivedCode -> 1
                ScreenToShow.ConfirmCurrentMail -> 2
                ScreenToShow.Working -> 3
            }
        })

        model.mailAuthToken.observe(this, Observer {
            if (it != null) {
                listener.onLoginSucceeded(it)
            }
        })

        model.errorMessage.observe(this, Observer {
            if (it != null) {
                if (it == ErrorMessage.BlacklistedMailServer) {
                    BlacklistedMailServerDialogFragment().show(fragmentManager!!)
                } else if (it == ErrorMessage.NotWhitelistedMailAddress) {
                    NotWhitelistedMailAddressDialogFragment().show(fragmentManager!!)
                } else {
                    Snackbar.make(
                            binding.root,
                            when(it) {
                                ErrorMessage.NetworkProblem -> R.string.error_network
                                ErrorMessage.ServerRejection -> R.string.error_server_rejected
                                ErrorMessage.WrongCode -> R.string.authenticate_by_mail_snackbar_wrong_code
                                ErrorMessage.BlacklistedMailServer -> throw IllegalStateException("should be handled above")
                                ErrorMessage.NotWhitelistedMailAddress -> throw IllegalStateException("should be handled above")
                            },
                            Snackbar.LENGTH_SHORT
                    ).show()
                }

                model.errorMessage.value = null
            }
        })

        return binding.root
    }
}

interface AuthenticateByMailFragmentListener {
    fun onLoginSucceeded(mailAuthToken: String)
}
