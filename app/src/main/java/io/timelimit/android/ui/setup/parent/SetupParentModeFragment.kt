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
package io.timelimit.android.ui.setup.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.jaredrummler.android.device.DeviceName
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentSetupParentModeBinding
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.network.StatusOfMailAddress
import io.timelimit.android.ui.authentication.AuthenticateByMailFragment
import io.timelimit.android.ui.authentication.AuthenticateByMailFragmentListener
import io.timelimit.android.ui.update.UpdateConsentCard

class SetupParentModeFragment : Fragment(), AuthenticateByMailFragmentListener {
    val model: SetupParentModeModel by lazy { ViewModelProviders.of(this).get(SetupParentModeModel::class.java) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentSetupParentModeBinding.inflate(layoutInflater, container, false)

        model.mailAuthToken.switchMap {
            mailAuthToken ->

            if (mailAuthToken == null) {
                liveDataFromValue(1)   // show login screen
            } else {
                // show form or loading indicator or error screen
                model.statusOfMailAddress.switchMap {
                    status ->

                    if (status == null) {
                        liveDataFromValue(2)    // loading screen
                    } else if (status.status == StatusOfMailAddress.MailAddressWithoutFamily && status.canCreateFamily == false) {
                        liveDataFromValue(3)    // signup disabled screen
                    } else {
                        model.isDoingSetup.map {
                            if (it!!) {
                                2   // loading screen
                            } else {
                                0   // the form
                            }
                        }
                    }
                }
            }
        }.observe(viewLifecycleOwner, Observer {
            binding.switcher.displayedChild = it!!
        })

        model.statusOfMailAddress.observe(viewLifecycleOwner, Observer {
            if (it != null) {
                binding.isNewFamily = when (it.status) {
                    StatusOfMailAddress.MailAddressWithoutFamily -> true
                    StatusOfMailAddress.MailAddressWithFamily -> false
                }

                binding.showLimitedProInfo = !it.alwaysPro
                binding.mail = it.mail
            }
        })

        // TODO: require that an device name and an parent name are set
        val isInputValid = model.statusOfMailAddress.switchMap {
            if (it == null) {
                liveDataFromValue(false)
            } else {
                when (it.status) {
                    StatusOfMailAddress.MailAddressWithFamily -> liveDataFromValue(true)
                    StatusOfMailAddress.MailAddressWithoutFamily -> binding.password.passwordOk
                }
            }
        }

        isInputValid.observe(viewLifecycleOwner, Observer {
            binding.enableOkButton = it!!
        })

        if (savedInstanceState == null) {
            // provide an useful default value
            binding.deviceName.setText(DeviceName.getDeviceName())
        }

        binding.ok.setOnClickListener {
            val status = model.statusOfMailAddress.value

            if (status == null) {
                throw IllegalStateException()
            }

            when (status.status) {
                StatusOfMailAddress.MailAddressWithoutFamily -> {
                    model.createFamily(
                            parentPassword = binding.password.readPassword(),
                            parentName = binding.prename.text.toString(),
                            deviceName = binding.deviceName.text.toString(),
                            enableBackgroundSync = binding.backgroundSyncCheckbox.isChecked,
                            enableUpdateChecks = binding.update.enableSwitch.isChecked
                    )
                }
                StatusOfMailAddress.MailAddressWithFamily -> {
                    model.addDeviceToFamily(
                            deviceName = binding.deviceName.text.toString(),
                            enableBackgroundSync = binding.backgroundSyncCheckbox.isChecked,
                            enableUpdateChecks = binding.update.enableSwitch.isChecked
                    )
                }
            }
        }

        model.isSetupDone.observe(this, Observer {
            if (it!!) {
                Navigation.findNavController(binding.root).popBackStack(R.id.overviewFragment, false)
            }
        })

        UpdateConsentCard.bind(
                view = binding.update,
                lifecycleOwner = viewLifecycleOwner,
                database = DefaultAppLogic.with(context!!).database
        )

        return binding.root
    }

    override fun onLoginSucceeded(mailAuthToken: String) = model.setMailToken(mailAuthToken)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.mail_auth_container, AuthenticateByMailFragment())
                    .commit()
        }
    }
}
