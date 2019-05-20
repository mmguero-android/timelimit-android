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
package io.timelimit.android.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import io.timelimit.android.data.model.Notification

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification LIMIT :pageSize OFFSET :offset")
    fun getNotificationPageSync(offset: Int, pageSize: Int): List<Notification>

    @Insert
    fun addNotificationSync(notification: Notification)

    @Insert
    fun addNotificationsSync(notifications: List<Notification>)

    @Delete
    fun removeNotificationSync(notifications: List<Notification>)

    @Query("SELECT * FROM notification WHERE dismissed = 0")
    fun getVisibleNotifications(): List<Notification>

    @Query("SELECT * FROM notification")
    fun getAllNotifications(): List<Notification>

    @Query("SELECT * FROM notification WHERE dismissed = 0 AND first_notify_time > :now ORDER BY first_notify_time ASC LIMIT 1")
    fun getNextVisibleNotifications(now: Long): Notification?

    @Query("UPDATE notification SET dismissed = 1 WHERE type = :type AND id = :id")
    fun setItemDismissed(type: Int, id: String)
}