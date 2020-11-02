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
package io.timelimit.android.ui.lock

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.timelimit.android.R
import io.timelimit.android.data.extensions.sortedCategories
import io.timelimit.android.data.model.*
import io.timelimit.android.data.model.derived.DeviceRelatedData
import io.timelimit.android.data.model.derived.UserRelatedData
import io.timelimit.android.databinding.LockFragmentCategoryButtonBinding
import io.timelimit.android.databinding.LockReasonFragmentBinding
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.livedata.*
import io.timelimit.android.logic.*
import io.timelimit.android.sync.actions.AddCategoryAppsAction
import io.timelimit.android.sync.actions.IncrementCategoryExtraTimeAction
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.sync.actions.UpdateNetworkTimeVerificationAction
import io.timelimit.android.sync.network.UpdatePrimaryDeviceRequestType
import io.timelimit.android.ui.MainActivity
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.settings.networks.RequestWifiPermission
import io.timelimit.android.ui.manage.child.advanced.managedisabletimelimits.ManageDisableTimelimitsViewHelper
import io.timelimit.android.ui.manage.child.category.create.CreateCategoryDialogFragment
import io.timelimit.android.ui.manage.child.primarydevice.UpdatePrimaryDeviceDialogFragment
import io.timelimit.android.ui.payment.RequiresPurchaseDialogFragment
import io.timelimit.android.ui.view.SelectTimeSpanViewListener
import java.util.*

class LockReasonFragment : Fragment() {
    companion object {
        private const val LOCATION_REQUEST_CODE = 1
    }

    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private lateinit var binding: LockReasonFragmentBinding
    private val model: LockModel by activityViewModels()

    private fun setupHandlers(deviceId: String, userRelatedData: UserRelatedData, blockedCategoryId: String?) {
        binding.handlers = object: Handlers {
            override fun openMainApp() {
                startActivity(Intent(context, MainActivity::class.java))
            }

            override fun allowTemporarily() {
                if (auth.requestAuthenticationOrReturnTrue()) model.allowAppTemporarily()
            }

            override fun confirmLocalTime() {
                if (auth.requestAuthenticationOrReturnTrue()) model.confirmLocalTime()
            }

            override fun disableTimeVerification() {
                auth.tryDispatchParentAction(
                        UpdateNetworkTimeVerificationAction(
                                deviceId = deviceId,
                                mode = NetworkTime.IfPossible
                        )
                )
            }

            override fun disableTemporarilyLockForAllCategories() {
                auth.tryDispatchParentActions(
                        userRelatedData.categories
                                .filter { it.category.temporarilyBlocked }
                                .map {
                                    UpdateCategoryTemporarilyBlockedAction(
                                            categoryId = it.category.id,
                                            blocked = false,
                                            endTime = null
                                    )
                                }
                )
            }

            override fun disableTemporarilyLockForCurrentCategory() {
                blockedCategoryId ?: return

                auth.tryDispatchParentAction(
                        UpdateCategoryTemporarilyBlockedAction(
                                categoryId = blockedCategoryId,
                                blocked = false,
                                endTime = null
                        )
                )
            }

            override fun showAuthenticationScreen() {
                (activity as LockActivity).showAuthenticationScreen()
            }

            override fun setThisDeviceAsCurrentDevice() = this@LockReasonFragment.setThisDeviceAsCurrentDevice()

            override fun requestLocationPermission() {
                RequestWifiPermission.doRequest(this@LockReasonFragment, LOCATION_REQUEST_CODE)
            }
        }
    }

    private fun setThisDeviceAsCurrentDevice() {
        model.didOpenSetCurrentDeviceScreen = true

        UpdatePrimaryDeviceDialogFragment
                .newInstance(UpdatePrimaryDeviceRequestType.SetThisDevice)
                .show(parentFragmentManager)
    }

    private fun bindAddToCategoryOptions(userRelatedData: UserRelatedData, blockedPackageName: String) {
        binding.addToCategoryOptions.removeAllViews()

        userRelatedData.sortedCategories().forEach { (_, category) ->
            LockFragmentCategoryButtonBinding.inflate(LayoutInflater.from(context), binding.addToCategoryOptions, true).let { button ->
                button.title = category.category.title
                button.button.setOnClickListener { _ ->
                    auth.tryDispatchParentAction(
                            AddCategoryAppsAction(
                                    categoryId = category.category.id,
                                    packageNames = listOf(blockedPackageName)
                            )
                    )
                }
            }
        }

        LockFragmentCategoryButtonBinding.inflate(LayoutInflater.from(context), binding.addToCategoryOptions, true).let { button ->
            button.title = getString(R.string.create_category_title)
            button.button.setOnClickListener {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    CreateCategoryDialogFragment
                            .newInstance(childId = userRelatedData.user.id)
                            .show(parentFragmentManager)
                }
            }
        }
    }

    private fun bindExtraTimeView(deviceRelatedData: DeviceRelatedData, categoryId: String, timeZone: TimeZone) {
        val hasFullVersion = deviceRelatedData.isConnectedAndHasPremium || deviceRelatedData.isLocalMode

        binding.extraTimeBtnOk.setOnClickListener {
            binding.extraTimeSelection.clearNumberPickerFocus()

            if (hasFullVersion) {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    val extraTimeToAdd = binding.extraTimeSelection.timeInMillis

                    if (extraTimeToAdd > 0) {
                        binding.extraTimeBtnOk.isEnabled = false

                        val date = DateInTimezone.newInstance(auth.logic.timeApi.getCurrentTimeInMillis(), timeZone)

                        auth.tryDispatchParentAction(IncrementCategoryExtraTimeAction(
                                categoryId = categoryId,
                                addedExtraTime = extraTimeToAdd,
                                extraTimeDay = if (binding.switchLimitExtraTimeToToday.isChecked) date.dayOfEpoch else -1
                        ))

                        binding.extraTimeBtnOk.isEnabled = true
                    }
                }
            } else {
                RequiresPurchaseDialogFragment().show(parentFragmentManager)
            }
        }
    }

    private fun initExtraTimeView() {
        binding.extraTimeTitle.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.lock_extratime_title,
                    text = R.string.lock_extratime_text
            ).show(parentFragmentManager)
        }

        model.enableAlternativeDurationSelection.observe(viewLifecycleOwner) { binding.extraTimeSelection.enablePickerMode(it) }

        binding.extraTimeSelection.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) {
                binding.extraTimeBtnOk.visibility = if (newTimeInMillis == 0L) View.GONE else View.VISIBLE
            }

            override fun setEnablePickerMode(enable: Boolean) { model.setEnablePickerMode(enable) }
        }
    }

    private fun initGrantPermissionView() {
        model.missingNetworkIdPermission.observe(viewLifecycleOwner) { binding.missingNetworkIdPermission = it }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = LockReasonFragmentBinding.inflate(layoutInflater, container, false)

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                fragment = this,
                doesSupportAuth = liveDataFromValue(true)
        )

        model.osClockInMillis.observe(viewLifecycleOwner) { systemTime ->
            binding.currentTime = DateUtils.formatDateTime(
                    context,
                    systemTime!!,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                            DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY
            )
        }

        model.packageAndActivityNameLive.observe(viewLifecycleOwner) { binding.packageName = it.first }

        binding.appTitle = model.title ?: "???"
        binding.appIcon.setImageDrawable(model.icon)

        initExtraTimeView()
        initGrantPermissionView()

        model.content.observe(viewLifecycleOwner) { content ->
            when (content) {
                LockscreenContent.Close -> {
                    binding.reason = BlockingReason.None
                    binding.handlers = null

                    requireActivity().finish()
                }
                is LockscreenContent.Blocked -> {
                    binding.activityName = if (content.enableActivityLevelBlocking) content.appActivityName?.removePrefix(content.appPackageName) else null

                    when (content) {
                        is LockscreenContent.Blocked.BlockedCategory -> {
                            binding.appCategoryTitle = content.appCategoryTitle
                            binding.reason = content.reason
                            binding.blockedKindLabel = when (content.level) {
                                BlockingLevel.Activity -> "Activity"
                                BlockingLevel.App -> "App"
                            }
                            setupHandlers(
                                    deviceId = content.deviceId,
                                    blockedCategoryId = content.blockedCategoryId,
                                    userRelatedData = content.userRelatedData
                            )
                            bindExtraTimeView(
                                    deviceRelatedData = content.deviceRelatedData,
                                    categoryId = content.blockedCategoryId,
                                    timeZone = content.userRelatedData.timeZone
                            )
                            binding.manageDisableTimeLimits.handlers = ManageDisableTimelimitsViewHelper.createHandlers(
                                    childId = content.userId,
                                    childTimezone = content.timeZone,
                                    activity = requireActivity(),
                                    hasFullVersion = content.hasFullVersion
                            )
                        }
                        is LockscreenContent.Blocked.BlockDueToNoCategory -> {
                            binding.appCategoryTitle = null
                            binding.reason = BlockingReason.NotPartOfAnCategory
                            binding.blockedKindLabel = "App"

                            setupHandlers(
                                    deviceId = content.deviceId,
                                    blockedCategoryId = null,
                                    userRelatedData = content.userRelatedData
                            )

                            bindAddToCategoryOptions(
                                    userRelatedData = content.userRelatedData,
                                    blockedPackageName = content.appPackageName
                            )
                        }
                    }.let {/* require handling all paths */}
                }
            }.let {/* require handling all paths */}
        }

        return binding.root
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.find { it != PackageManager.PERMISSION_GRANTED } != null) {
            Toast.makeText(requireContext(), R.string.generic_runtime_permission_rejected, Toast.LENGTH_LONG).show()
        }
    }
}

interface Handlers {
    fun openMainApp()
    fun allowTemporarily()
    fun confirmLocalTime()
    fun disableTimeVerification()
    fun disableTemporarilyLockForCurrentCategory()
    fun disableTemporarilyLockForAllCategories()
    fun showAuthenticationScreen()
    fun setThisDeviceAsCurrentDevice()
    fun requestLocationPermission()
}
