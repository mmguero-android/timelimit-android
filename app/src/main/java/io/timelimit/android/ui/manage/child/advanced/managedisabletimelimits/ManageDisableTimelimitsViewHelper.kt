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
package io.timelimit.android.ui.manage.child.advanced.managedisabletimelimits

import android.content.Context
import android.text.format.DateUtils
import androidx.fragment.app.FragmentActivity
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserType
import io.timelimit.android.date.DateInTimezone
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.logic.RealTime
import io.timelimit.android.sync.actions.SetUserDisableLimitsUntilAction
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.payment.RequiresPurchaseDialogFragment
import io.timelimit.android.ui.view.ManageDisableTimelimitsViewHandlers
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import java.util.*

object ManageDisableTimelimitsViewHelper {
    fun createHandlers(childId: String, childTimezone: String, activity: FragmentActivity, hasFullVersion: Boolean): ManageDisableTimelimitsViewHandlers {
        val auth = getActivityViewModel(activity)
        val logic = DefaultAppLogic.with(activity)

        fun getCurrentTime() = RealTime.newInstance().apply {
            logic.realTimeLogic.getRealTime(this)
        }.timeInMillis

        return object : ManageDisableTimelimitsViewHandlers {
            override fun disableTimeLimitsForDuration(duration: Long) {
                if (!hasFullVersion) {
                    RequiresPurchaseDialogFragment().show(activity.supportFragmentManager)
                    return
                }

                auth.tryDispatchParentAction(
                        SetUserDisableLimitsUntilAction(
                                childId = childId,
                                timestamp = getCurrentTime() + duration
                        )
                )
            }

            override fun disableTimeLimitsForToday() {
                if (!hasFullVersion) {
                    RequiresPurchaseDialogFragment().show(activity.supportFragmentManager)
                    return
                }

                val dayOfEpoch = DateInTimezone.newInstance(getCurrentTime(), TimeZone.getTimeZone(childTimezone)).dayOfEpoch.toLong()

                val nextDayStart = LocalDate.ofEpochDay(dayOfEpoch)
                        .plusDays(1)
                        .atStartOfDay(ZoneId.of(childTimezone))
                        .toEpochSecond() * 1000

                auth.tryDispatchParentAction(
                        SetUserDisableLimitsUntilAction(
                                childId = childId,
                                timestamp = nextDayStart
                        )
                )
            }

            override fun disableTimeLimitsUntilSelectedDate() {
                if (!hasFullVersion) {
                    RequiresPurchaseDialogFragment().show(activity.supportFragmentManager)
                    return
                }

                if (auth.requestAuthenticationOrReturnTrue()) {
                    DisableTimelimitsUntilDateDialogFragment.newInstance(childId).show(activity.supportFragmentManager)
                }
            }

            override fun disableTimeLimitsUntilSelectedTimeOfToday() {
                if (!hasFullVersion) {
                    RequiresPurchaseDialogFragment().show(activity.supportFragmentManager)
                    return
                }

                if (auth.requestAuthenticationOrReturnTrue()) {
                    DisableTimelimitsUntilTimeDialogFragment.newInstance(childId).show(activity.supportFragmentManager)
                }
            }

            override fun enableTimeLimits() {
                auth.tryDispatchParentAction(
                        SetUserDisableLimitsUntilAction(
                                childId = childId,
                                timestamp = 0
                        )
                )
            }

            override fun showDisableTimeLimitsHelp() {
                HelpDialogFragment.newInstance(
                        title = R.string.manage_disable_time_limits_title,
                        text = R.string.manage_disable_time_limits_text
                ).show(activity.supportFragmentManager)
            }
        }
    }

    fun getDisabledUntilString(child: User?, currentTime: Long, context: Context): String? {
        if (child == null || child.type != UserType.Child || child.disableLimitsUntil == 0L || child.disableLimitsUntil < currentTime) {
            return null
        } else {
            return DateUtils.formatDateTime(
                    context,
                    child.disableLimitsUntil,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                            DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY
            )
        }
    }
}
