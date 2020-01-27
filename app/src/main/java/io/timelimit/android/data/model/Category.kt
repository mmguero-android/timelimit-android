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
package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.customtypes.ImmutableBitmaskAdapter
import io.timelimit.android.data.customtypes.ImmutableBitmaskJson
import java.util.*

@Entity(tableName = "category")
@TypeConverters(ImmutableBitmaskAdapter::class)
data class Category(
        @PrimaryKey
        @ColumnInfo(name = "id")
        val id: String,
        @ColumnInfo(name = "child_id")
        val childId: String,
        @ColumnInfo(name = "title")
        val title: String,
        @ColumnInfo(name = "blocked_times")
        val blockedMinutesInWeek: ImmutableBitmask,    // 10080 bit -> ~10 KB
        @ColumnInfo(name = "extra_time")
        val extraTimeInMillis: Long,
        @ColumnInfo(name = "temporarily_blocked")
        val temporarilyBlocked: Boolean,
        @ColumnInfo(name = "temporarily_blocked_end_time")
        val temporarilyBlockedEndTime: Long,
        @ColumnInfo(name = "base_version")
        val baseVersion: String,
        @ColumnInfo(name = "apps_version")
        val assignedAppsVersion: String,
        @ColumnInfo(name = "rules_version")
        val timeLimitRulesVersion: String,
        @ColumnInfo(name = "usedtimes_version")
        val usedTimesVersion: String,
        @ColumnInfo(name = "parent_category_id")
        val parentCategoryId: String,
        @ColumnInfo(name = "block_all_notifications")
        val blockAllNotifications: Boolean,
        @ColumnInfo(name = "time_warnings")
        val timeWarnings: Int,
        @ColumnInfo(name = "min_battery_charging")
        val minBatteryLevelWhileCharging: Int,
        @ColumnInfo(name = "min_battery_mobile")
        val minBatteryLevelMobile: Int
): JsonSerializable {
    companion object {
        const val MINUTES_PER_DAY = 60 * 24
        const val BLOCKED_MINUTES_IN_WEEK_LENGTH = MINUTES_PER_DAY * 7

        private const val ID = "id"
        private const val CHILD_ID = "cid"
        private const val TITLE = "T"
        private const val BLOCKED_MINUTES_IN_WEEK = "b"
        private const val EXTRA_TIME_IN_MILLIS = "et"
        private const val TEMPORARILY_BLOCKED = "tb"
        private const val TEMPORARILY_BLOCKED_NED_TIME = "tbet"
        private const val BASE_VERSION = "vb"
        private const val ASSIGNED_APPS_VERSION = "va"
        private const val RULES_VERSION = "vr"
        private const val USED_TIMES_VERSION = "vu"
        private const val PARENT_CATEGORY_ID = "pc"
        private const val BlOCK_ALL_NOTIFICATIONS = "ban"
        private const val TIME_WARNINGS = "tw"
        private const val MIN_BATTERY_CHARGING = "minBatteryCharging"
        private const val MIN_BATTERY_MOBILE = "minBatteryMobile"

        fun parse(reader: JsonReader): Category {
            var id: String? = null
            var childId: String? = null
            var title: String? = null
            var blockedMinutesInWeek: ImmutableBitmask? = null
            var extraTimeInMillis: Long? = null
            var temporarilyBlocked: Boolean? = null
            var temporarilyBlockedEndTime: Long = 0
            var baseVersion: String? = null
            var assignedAppsVersion: String? = null
            var timeLimitRulesVersion: String? = null
            var usedTimesVersion: String? = null
            // this field was added later so it has got a default value
            var parentCategoryId = ""
            var blockAllNotifications = false
            var timeWarnings = 0
            var minBatteryCharging = 0
            var minBatteryMobile = 0

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    ID -> id = reader.nextString()
                    CHILD_ID -> childId = reader.nextString()
                    TITLE -> title = reader.nextString()
                    BLOCKED_MINUTES_IN_WEEK -> blockedMinutesInWeek = ImmutableBitmaskJson.parse(reader.nextString(), BLOCKED_MINUTES_IN_WEEK_LENGTH)
                    EXTRA_TIME_IN_MILLIS -> extraTimeInMillis = reader.nextLong()
                    TEMPORARILY_BLOCKED -> temporarilyBlocked = reader.nextBoolean()
                    TEMPORARILY_BLOCKED_NED_TIME -> temporarilyBlockedEndTime = reader.nextLong()
                    BASE_VERSION -> baseVersion = reader.nextString()
                    ASSIGNED_APPS_VERSION -> assignedAppsVersion = reader.nextString()
                    RULES_VERSION -> timeLimitRulesVersion = reader.nextString()
                    USED_TIMES_VERSION -> usedTimesVersion = reader.nextString()
                    PARENT_CATEGORY_ID -> parentCategoryId = reader.nextString()
                    BlOCK_ALL_NOTIFICATIONS -> blockAllNotifications = reader.nextBoolean()
                    TIME_WARNINGS -> timeWarnings = reader.nextInt()
                    MIN_BATTERY_CHARGING -> minBatteryCharging = reader.nextInt()
                    MIN_BATTERY_MOBILE -> minBatteryMobile = reader.nextInt()
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return Category(
                    id = id!!,
                    childId = childId!!,
                    title = title!!,
                    blockedMinutesInWeek = blockedMinutesInWeek!!,
                    extraTimeInMillis = extraTimeInMillis!!,
                    temporarilyBlocked = temporarilyBlocked!!,
                    temporarilyBlockedEndTime = temporarilyBlockedEndTime,
                    baseVersion = baseVersion!!,
                    assignedAppsVersion = assignedAppsVersion!!,
                    timeLimitRulesVersion = timeLimitRulesVersion!!,
                    usedTimesVersion = usedTimesVersion!!,
                    parentCategoryId = parentCategoryId,
                    blockAllNotifications = blockAllNotifications,
                    timeWarnings = timeWarnings,
                    minBatteryLevelWhileCharging = minBatteryCharging,
                    minBatteryLevelMobile = minBatteryMobile
            )
        }
    }

    init {
        IdGenerator.assertIdValid(id)
        IdGenerator.assertIdValid(childId)

        if (extraTimeInMillis < 0) {
            throw IllegalStateException()
        }

        if (title.isEmpty()) {
            throw IllegalArgumentException()
        }

        if (minBatteryLevelMobile < 0 || minBatteryLevelWhileCharging < 0) {
            throw IllegalArgumentException()
        }

        if (minBatteryLevelMobile > 100 || minBatteryLevelWhileCharging > 100) {
            throw IllegalArgumentException()
        }
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(ID).value(id)
        writer.name(CHILD_ID).value(childId)
        writer.name(TITLE).value(title)
        writer.name(BLOCKED_MINUTES_IN_WEEK).value(ImmutableBitmaskJson.serialize(blockedMinutesInWeek))
        writer.name(EXTRA_TIME_IN_MILLIS).value(extraTimeInMillis)
        writer.name(TEMPORARILY_BLOCKED).value(temporarilyBlocked)
        writer.name(TEMPORARILY_BLOCKED_NED_TIME).value(temporarilyBlockedEndTime)
        writer.name(BASE_VERSION).value(baseVersion)
        writer.name(ASSIGNED_APPS_VERSION).value(assignedAppsVersion)
        writer.name(RULES_VERSION).value(timeLimitRulesVersion)
        writer.name(USED_TIMES_VERSION).value(usedTimesVersion)
        writer.name(PARENT_CATEGORY_ID).value(parentCategoryId)
        writer.name(BlOCK_ALL_NOTIFICATIONS).value(blockAllNotifications)
        writer.name(TIME_WARNINGS).value(timeWarnings)
        writer.name(MIN_BATTERY_CHARGING).value(minBatteryLevelWhileCharging)
        writer.name(MIN_BATTERY_MOBILE).value(minBatteryLevelMobile)

        writer.endObject()
    }
}

object CategoryTimeWarnings {
    val durationToBitIndex = mapOf(
            1000L * 60 to 0, // 1 minute
            1000L * 60 * 3 to 1, // 3 minutes
            1000L * 60 * 5 to 2, // 5 minutes
            1000L * 60 * 10 to 3, // 10 minutes
            1000L * 60 * 15 to 4 // 15 minutes
    )

    val durations = durationToBitIndex.keys
}

fun ImmutableBitmask.withConfigCopiedToOtherDates(sourceDay: Int, targetDays: Set<Int>): ImmutableBitmask {
    val result = dataNotToModify.clone() as BitSet

    val configForSelectedDay = result.get(
            sourceDay * Category.MINUTES_PER_DAY,
            (sourceDay + 1) * Category.MINUTES_PER_DAY
    )

    // update all days
    targetDays.forEach { day ->
        val startWriteIndex = day * Category.MINUTES_PER_DAY

        for (i in 0..(Category.MINUTES_PER_DAY - 1)) {
            result[startWriteIndex + i] = configForSelectedDay[i]
        }
    }

    return ImmutableBitmask(result)
}