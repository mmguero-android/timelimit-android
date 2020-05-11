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

package io.timelimit.android.update

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter

data class UpdateStatus(
        val versionCode: Int, val versionName: String, val url: String, val sha512: String,
        val changelogDe: String?, val changelogDefault: String
) {
    companion object {
        private const val VERSION_CODE = "versionCode"
        private const val VERSION_NAME = "versionName"
        private const val URL = "url"
        private const val SHA512 = "sha512"
        private const val CHANGELOG = "changelog"
        private const val DE = "de"
        private const val DEFAULT = "default"

        fun parse(reader: JsonReader): UpdateStatus {
            var versionCode: Int? = null
            var versionName: String? = null
            var url: String? = null
            var sha512: String? = null
            var changelogDe: String? = null
            var changelogDefault: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    VERSION_CODE -> versionCode = reader.nextInt()
                    VERSION_NAME -> versionName = reader.nextString()
                    URL -> url = reader.nextString()
                    SHA512 -> sha512 = reader.nextString()
                    CHANGELOG -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                DE -> changelogDe = reader.nextString()
                                DEFAULT -> changelogDefault = reader.nextString()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return UpdateStatus(
                    versionCode = versionCode!!,
                    versionName = versionName!!,
                    url = url!!,
                    sha512 = sha512!!,
                    changelogDe = changelogDe,
                    changelogDefault = changelogDefault!!
            )
        }
    }

    fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(VERSION_CODE).value(versionCode)
        writer.name(VERSION_NAME).value(versionName)
        writer.name(URL).value(url)
        writer.name(SHA512).value(sha512)

        writer.name(CHANGELOG).beginObject()
        writer.name(DEFAULT).value(changelogDefault)
        if (changelogDe != null) {
            writer.name(DE).value(changelogDe)
        }
        writer.endObject()

        writer.endObject()
    }

    fun getChangelog(context: Context) = if (context.resources.configuration.locale.language == "de") {
        changelogDe
    } else {
        changelogDefault
    }
}