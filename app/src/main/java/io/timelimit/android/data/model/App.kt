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
package io.timelimit.android.data.model

import android.util.JsonReader
import android.util.JsonWriter
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable

@Entity(primaryKeys = ["device_id", "package_name"], tableName = "app")
@TypeConverters(AppRecommendationConverter::class)
data class App (
        @ColumnInfo(index = true, name = "device_id")
        val deviceId: String,
        @ColumnInfo(index = true, name = "package_name")
        val packageName: String,
        @ColumnInfo(name = "title")
        val title: String,
        @ColumnInfo(name = "launchable")
        val isLaunchable: Boolean,
        @ColumnInfo(name = "recommendation")
        val recommendation: AppRecommendation
): JsonSerializable {
    companion object {
        private const val DEVICE_ID = "d"
        private const val PACKAGE_NAME = "p"
        private const val TITLE = "t"
        private const val IS_LAUNCHABLE = "l"
        private const val RECOMMENDATION = "r"

        fun parse(reader: JsonReader): App {
            var deviceId: String? = null
            var packageName: String? = null
            var title: String? = null
            var isLaunchable: Boolean? = null
            var recommendation: AppRecommendation? = null

            reader.beginObject()

            while (reader.hasNext()) {
                when (reader.nextName()) {
                    DEVICE_ID -> deviceId = reader.nextString()
                    PACKAGE_NAME -> packageName = reader.nextString()
                    TITLE -> title = reader.nextString()
                    IS_LAUNCHABLE -> isLaunchable = reader.nextBoolean()
                    RECOMMENDATION -> recommendation = AppRecommendationJson.parse(reader.nextString())
                    else -> reader.skipValue()
                }
            }

            reader.endObject()

            return App(
                    deviceId = deviceId!!,
                    packageName = packageName!!,
                    title = title!!,
                    isLaunchable = isLaunchable!!,
                    recommendation = recommendation!!
            )
        }
    }

    init {
        IdGenerator.assertIdValid(deviceId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(DEVICE_ID).value(deviceId)
        writer.name(PACKAGE_NAME).value(packageName)
        writer.name(TITLE).value(title)
        writer.name(IS_LAUNCHABLE).value(isLaunchable)
        writer.name(RECOMMENDATION).value(AppRecommendationJson.serialize(recommendation))

        writer.endObject()
    }
}

enum class AppRecommendation {
    None, Whitelist, Blacklist
}

class AppRecommendationConverter {
    @TypeConverter
    fun toAppRecommendation(value: String) = AppRecommendationJson.parse(value)

    @TypeConverter
    fun toString(value: AppRecommendation) = AppRecommendationJson.serialize(value)
}

object AppRecommendationJson {
    fun parse(value: String) = when(value) {
        "whitelist" -> AppRecommendation.Whitelist
        "blacklist" -> AppRecommendation.Blacklist
        "none" -> AppRecommendation.None
        else -> throw IllegalArgumentException()
    }

    fun serialize(value: AppRecommendation) = when(value) {
        AppRecommendation.None -> "none"
        AppRecommendation.Blacklist -> "blacklist"
        AppRecommendation.Whitelist -> "whitelist"
    }
}
