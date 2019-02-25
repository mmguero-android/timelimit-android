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
package io.timelimit.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.timelimit.android.data.model.*

@Database(entities = [
    User::class,
    Device::class,
    App::class,
    CategoryApp::class,
    Category::class,
    UsedTimeItem::class,
    TimeLimitRule::class,
    ConfigurationItem::class,
    TemporarilyAllowedApp::class,
    PendingSyncAction::class
], version = 11)
abstract class RoomDatabase: RoomDatabase(), io.timelimit.android.data.Database {
    companion object {
        private val lock = Object()
        private var instance: io.timelimit.android.data.Database? = null
        const val DEFAULT_DB_NAME = "db"
        const val BACKUP_DB_NAME = "db2"

        fun with(context: Context): io.timelimit.android.data.Database {
            if (instance == null) {
                synchronized(lock) {
                    if (instance == null) {
                        instance = createOrOpenLocalStorageInstance(context, DEFAULT_DB_NAME)
                    }
                }
            }

            return instance!!
        }

        fun createInMemoryInstance(context: Context): io.timelimit.android.data.Database {
            return Room.inMemoryDatabaseBuilder(
                    context,
                    io.timelimit.android.data.RoomDatabase::class.java
            ).build()
        }

        fun createOrOpenLocalStorageInstance(context: Context, name: String): io.timelimit.android.data.Database {
            return Room.databaseBuilder(
                    context,
                    io.timelimit.android.data.RoomDatabase::class.java,
                    name
            )
                    .setJournalMode(JournalMode.TRUNCATE)
                    .fallbackToDestructiveMigration()
                    .addMigrations(
                            DatabaseMigrations.MIGRATE_TO_V2,
                            DatabaseMigrations.MIGRATE_TO_V3,
                            DatabaseMigrations.MIGRATE_TO_V4,
                            DatabaseMigrations.MIGRATE_TO_V5,
                            DatabaseMigrations.MIGRATE_TO_V6,
                            DatabaseMigrations.MIGRATE_TO_V7,
                            DatabaseMigrations.MIGRATE_TO_V8,
                            DatabaseMigrations.MIGRATE_TO_V9,
                            DatabaseMigrations.MIGRATE_TO_V10,
                            DatabaseMigrations.MIGRATE_TO_V11
                    )
                    .build()
        }
    }

    // the room compiler needs this
    override fun setTransactionSuccessful() {
        super.setTransactionSuccessful()
    }

    override fun beginTransaction() {
        super.beginTransaction()
    }

    override fun endTransaction() {
        super.endTransaction()
    }

    override fun deleteAllData() {
        clearAllTables()
    }

    override fun close() {
        super.close()
    }
}
