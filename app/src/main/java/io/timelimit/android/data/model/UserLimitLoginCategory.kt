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
import androidx.room.*
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.JsonSerializable

@Entity(
        tableName = "user_limit_login_category",
        indices = [
            Index(
                    name = "user_limit_login_category_index_category_id",
                    value = ["category_id"]
            )
        ],
        foreignKeys = [
            ForeignKey(
                    entity = User::class,
                    childColumns = ["user_id"],
                    parentColumns = ["id"],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            ),
            ForeignKey(
                    entity = Category::class,
                    childColumns = ["category_id"],
                    parentColumns = ["id"],
                    onDelete = ForeignKey.CASCADE,
                    onUpdate = ForeignKey.CASCADE
            )
        ]
)
data class UserLimitLoginCategory(
        @PrimaryKey
        @ColumnInfo(name = "user_id")
        val userId: String,
        @ColumnInfo(name = "category_id")
        val categoryId: String
): JsonSerializable {
    companion object {
        private const val USER_ID = "userId"
        private const val CATEGORY_ID = "categoryId"

        fun parse(reader: JsonReader): UserLimitLoginCategory {
            var userId: String? = null
            var categoryId: String? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    USER_ID -> userId = reader.nextString()
                    CATEGORY_ID -> categoryId = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()

            return UserLimitLoginCategory(
                    userId = userId!!,
                    categoryId = categoryId!!
            )
        }
    }

    init {
        IdGenerator.assertIdValid(userId)
        IdGenerator.assertIdValid(categoryId)
    }

    override fun serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(USER_ID).value(userId)
        writer.name(CATEGORY_ID).value(categoryId)

        writer.endObject()
    }
}

data class UserLimitLoginCategoryWithChildId(
        @ColumnInfo(name = "child_id")
        val childId: String,
        @ColumnInfo(name = "child_title")
        val childTitle: String,
        @ColumnInfo(name = "category_id")
        val categoryId: String,
        @ColumnInfo(name = "category_title")
        val categoryTitle: String,
        @ColumnInfo(name = "selected")
        val selected: Boolean
)