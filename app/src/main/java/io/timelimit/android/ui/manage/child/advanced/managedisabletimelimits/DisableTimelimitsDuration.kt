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
import io.timelimit.android.R
import io.timelimit.android.date.DateInTimezone
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import java.util.*

sealed class DisableTimelimitsOption {
    abstract fun getLabel(context: Context): String
}

object DisableTimelimitsUntilTimeOption: DisableTimelimitsOption() {
    override fun getLabel(context: Context): String = context.getString(R.string.manage_disable_time_limits_btn_time)
}

object DisableTimelimitsUntilDateOption: DisableTimelimitsOption() {
    override fun getLabel(context: Context): String = context.getString(R.string.manage_disable_time_limits_btn_date)
}

abstract class DisableTimelimitsDurationItem: DisableTimelimitsOption() {
    abstract fun getTime(currentTimestamp: Long, timezone: String): Long
}

class DisableTimelimitsDurationItemFixed(val label: Int, val duration: Long): DisableTimelimitsDurationItem() {
    override fun getLabel(context: Context) = context.getString(label)
    override fun getTime(currentTimestamp: Long, timezone: String): Long = currentTimestamp + duration
}

class DisableTimelimitsDurationItemFixedEndTime(val timestamp: Long): DisableTimelimitsDurationItem() {
    override fun getLabel(context: Context): String = context.getString(
            R.string.manage_child_block_temporarily_dialog_until,
            DateUtils.formatDateTime(
                    context,
                    timestamp,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                            DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_WEEKDAY
            )
    )

    override fun getTime(currentTimestamp: Long, timezone: String): Long = timestamp
}

class DisableTimelimitsDurationItemDays(val label: Int, val dayCounter: Int): DisableTimelimitsDurationItem() {
    override fun getLabel(context: Context) = context.getString(label)
    override fun getTime(currentTimestamp: Long, timezone: String): Long {
        return LocalDate.ofEpochDay(DateInTimezone.newInstance(currentTimestamp, TimeZone.getTimeZone(timezone)).dayOfEpoch.toLong())
                .plusDays(dayCounter.toLong())
                .atStartOfDay(ZoneId.of(timezone))
                .toEpochSecond() * 1000
    }
}

// FIXME: this is not used at the disable time limit view yet ...
object DisableTimelimitsDuration {
    val items = listOf(
            DisableTimelimitsDurationItemFixed(R.string.manage_disable_time_limits_btn_10_min, 1000 * 60 * 10),
            DisableTimelimitsDurationItemFixed(R.string.manage_disable_time_limits_btn_30_min, 1000 * 60 * 30),
            DisableTimelimitsDurationItemFixed(R.string.manage_disable_time_limits_btn_1_hour, 1000 * 60 * 60 * 1),
            DisableTimelimitsDurationItemFixed(R.string.manage_disable_time_limits_btn_2_hour, 1000 * 60 * 60 * 2),
            DisableTimelimitsDurationItemFixed(R.string.manage_disable_time_limits_btn_4_hour, 1000 * 60 * 60 * 4),
            DisableTimelimitsDurationItemDays(R.string.manage_disable_time_limits_btn_today, 1),
            DisableTimelimitsUntilTimeOption,
            DisableTimelimitsUntilDateOption
    )
}