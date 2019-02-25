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
package io.timelimit.android.date

import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.ChronoUnit
import java.util.*

data class DateInTimezone(val dayOfWeek: Int, val dayOfEpoch: Int) {
    companion object {
        fun convertDayOfWeek(dayOfWeek: Int) = when(dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> throw IllegalStateException()
        }

        fun newInstance(timeInMillis: Long, timeZone: TimeZone): DateInTimezone {
            val calendar = CalendarCache.getCalendar()

            calendar.firstDayOfWeek = Calendar.MONDAY

            calendar.timeZone = timeZone
            calendar.timeInMillis = timeInMillis

            val dayOfWeek = convertDayOfWeek(calendar.get(Calendar.DAY_OF_WEEK))

            val localDate = LocalDate.of(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.DAY_OF_MONTH)
            )

            val dayOfEpoch = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(0), localDate).toInt()

            return DateInTimezone(dayOfWeek, dayOfEpoch)
        }
    }
}
