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
package io.timelimit.android.ui.manage.device.manage.user


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.Navigation
import io.timelimit.android.R
import io.timelimit.android.data.model.Device
import io.timelimit.android.databinding.ManageDeviceUserFragmentBinding
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.SetDeviceUserAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.FragmentWithCustomTitle
import io.timelimit.android.ui.manage.device.manage.defaultuser.ManageDeviceDefaultUser

class ManageDeviceUserFragment : Fragment(), FragmentWithCustomTitle {
    private val activity: ActivityViewModelHolder by lazy { getActivity() as ActivityViewModelHolder }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val auth: ActivityViewModel by lazy { activity.getActivityViewModel() }
    private val args: ManageDeviceUserFragmentArgs by lazy { ManageDeviceUserFragmentArgs.fromBundle(arguments!!) }
    private val deviceEntry: LiveData<Device?> by lazy {
        logic.database.device().getDeviceById(args.deviceId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val navigation = Navigation.findNavController(container!!)
        val binding = ManageDeviceUserFragmentBinding.inflate(inflater, container, false)
        val userEntries = logic.database.user().getAllUsersLive()

        var isUpdatingSelectedUser = false

        // auth
        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromValue(true)
        )

        // label, id
        val userListItems = ArrayList<Pair<String, String>>()

        fun bindUserListItems() {
            userListItems.forEachIndexed { index, listItem ->
                val oldRadio = binding.userList.getChildAt(index) as RadioButton?
                val radio = oldRadio ?: RadioButton(context!!)

                radio.text = listItem.first

                if (oldRadio == null) {
                    radio.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    radio.id = index

                    binding.userList.addView(radio)
                }
            }

            while (binding.userList.childCount > userListItems.size) {
                binding.userList.removeViewAt(userListItems.size)
            }
        }

        fun bindUserListSelection() {
            isUpdatingSelectedUser = true

            val selectedUserId = deviceEntry.value?.currentUserId
            val selectedIndex = userListItems.indexOfFirst { it.second == selectedUserId }

            if (selectedIndex != -1) {
                binding.userList.check(selectedIndex)
            } else {
                val fallbackSelectedIndex = userListItems.indexOfFirst { it.second == "" }

                if (fallbackSelectedIndex != -1) {
                    binding.userList.check(fallbackSelectedIndex)
                }
            }

            isUpdatingSelectedUser = false
        }

        binding.handlers = object: ManageDeviceUserFragmentHandlers {
            override fun showAuthenticationScreen() {
                activity.showAuthenticationScreen()
            }
        }

        binding.userList.setOnCheckedChangeListener { _, checkedId ->
            val userId = userListItems[checkedId].second
            val device = deviceEntry.value

            if (device != null && device.currentUserId != userId && !isUpdatingSelectedUser) {
                if (!auth.tryDispatchParentAction(
                                SetDeviceUserAction(
                                        deviceId = args.deviceId,
                                        userId = userId
                                )
                        )) {
                    bindUserListSelection()
                }
            }
        }

        deviceEntry.observe(this, Observer {
            device ->

            if (device == null) {
                navigation.popBackStack(R.id.overviewFragment, false)
            }
        })

        val isThisDevice = logic.deviceId.map { ownDeviceId -> ownDeviceId == args.deviceId }.ignoreUnchanged()

        mergeLiveData(deviceEntry, userEntries).observe(this, Observer {
            val (device, users) = it!!

            if (device != null && users != null) {
                userListItems.clear()
                userListItems.addAll(
                        users.map { user -> Pair(user.name, user.id) }
                )
                userListItems.add(Pair(getString(R.string.manage_device_current_user_none), ""))

                bindUserListItems()
                bindUserListSelection()
            }
        })

        ManageDeviceDefaultUser.bind(
                view = binding.defaultUser,
                device = deviceEntry,
                users = userEntries,
                lifecycleOwner = this,
                isThisDevice = isThisDevice,
                auth = auth,
                fragmentManager = fragmentManager!!
        )

        return binding.root
    }

    override fun getCustomTitle() = deviceEntry.map { it?.name }
}

interface ManageDeviceUserFragmentHandlers {
    fun showAuthenticationScreen()
}
