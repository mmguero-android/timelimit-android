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
package io.timelimit.android.data.backup

import android.util.JsonReader
import android.util.JsonWriter
import io.timelimit.android.data.Database
import io.timelimit.android.data.JsonSerializable
import io.timelimit.android.data.model.*
import io.timelimit.android.data.transaction
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

object DatabaseBackupLowlevel {
    private const val PAGE_SIZE = 50

    private const val APP = "app"
    private const val CATEGORY = "category"
    private const val CATEGORY_APP = "categoryApp"
    private const val CONFIG = "config"
    private const val DEVICE = "device"
    private const val PENDING_SYNC_ACTION = "pendingSyncAction"
    private const val TIME_LIMIT_RULE = "timelimitRule"
    private const val USED_TIME_ITEM = "usedTime"
    private const val USER = "user"

    fun outputAsBackupJson(database: Database, outputStream: OutputStream) {
        val writer = JsonWriter(OutputStreamWriter(outputStream, Charsets.UTF_8))

        writer.beginObject()

        fun <T: JsonSerializable> handleCollection(
                name: String,
                readPage: (offset: Int, pageSize: Int) -> List<T>
        ) {
            writer.name(name).beginArray()

            var offset = 0

            while (true) {
                val page = readPage(offset, PAGE_SIZE)
                offset += page.size

                if (page.isEmpty()) {
                    break
                }

                page.forEach { it.serialize(writer) }
            }

            writer.endArray()
        }

        handleCollection(APP) {offset, pageSize -> database.app().getAppPageSync(offset, pageSize) }
        handleCollection(CATEGORY) {offset: Int, pageSize: Int -> database.category().getCategoryPageSync(offset, pageSize) }
        handleCollection(CATEGORY_APP) { offset, pageSize -> database.categoryApp().getCategoryAppPageSync(offset, pageSize) }

        writer.name(CONFIG).beginArray()
        database.config().getConfigItemsSync().forEach { it.serialize(writer) }
        writer.endArray()

        handleCollection(DEVICE) { offset, pageSize -> database.device().getDevicePageSync(offset, pageSize) }
        handleCollection(PENDING_SYNC_ACTION) { offset, pageSize -> database.pendingSyncAction().getPendingSyncActionPageSync(offset, pageSize) }
        handleCollection(TIME_LIMIT_RULE) { offset, pageSize -> database.timeLimitRules().getRulePageSync(offset, pageSize) }
        handleCollection(USED_TIME_ITEM) { offset, pageSize -> database.usedTimes().getUsedTimePageSync(offset, pageSize) }
        handleCollection(USER) { offset, pageSize -> database.user().getUserPageSync(offset, pageSize) }

        writer.endObject().flush()
    }

    fun restoreFromBackupJson(database: Database, inputStream: InputStream) {
        val reader = JsonReader(InputStreamReader(inputStream, Charsets.UTF_8))

        database.transaction().use {
            transaction ->

            database.deleteAllData()

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    APP -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.app().addAppSync(App.parse(reader))
                        }

                        reader.endArray()
                    }
                    CATEGORY -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.category().addCategory(Category.parse(reader))
                        }

                        reader.endArray()
                    }
                    CATEGORY_APP -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.categoryApp().addCategoryAppSync(CategoryApp.parse(reader))
                        }

                        reader.endArray()
                    }
                    CONFIG -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            val item = ConfigurationItem.parse(reader)

                            if (item != null) {
                                database.config().updateValueOfKeySync(item)
                            }
                        }

                        reader.endArray()
                    }
                    DEVICE -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.device().addDeviceSync(Device.parse(reader))
                        }

                        reader.endArray()
                    }
                    PENDING_SYNC_ACTION -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.pendingSyncAction().addSyncActionSync(PendingSyncAction.parse(reader))
                        }

                        reader.endArray()
                    }
                    TIME_LIMIT_RULE -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.timeLimitRules().addTimeLimitRule(TimeLimitRule.parse(reader))
                        }

                        reader.endArray()
                    }
                    USED_TIME_ITEM -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.usedTimes().insertUsedTime(UsedTimeItem.parse(reader))
                        }

                        reader.endArray()
                    }
                    USER -> {
                        reader.beginArray()

                        while (reader.hasNext()) {
                            database.user().addUserSync(User.parse(reader))
                        }

                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            transaction.setSuccess()
        }
    }
}