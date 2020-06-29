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

package io.timelimit.android.data.model.derived

import androidx.collection.LruCache
import io.timelimit.android.BuildConfig
import io.timelimit.android.data.Database
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.extensions.getTimezone
import io.timelimit.android.data.invalidation.Observer
import io.timelimit.android.data.invalidation.Table
import io.timelimit.android.data.model.CategoryApp
import io.timelimit.android.data.model.User
import java.lang.ref.WeakReference
import java.util.*

data class UserRelatedData(
        val user: User,
        val categories: List<CategoryRelatedData>,
        val categoryApps: List<CategoryApp>
): Observer {
    companion object {
        private val notFoundCategoryApp = CategoryApp(categoryId = IdGenerator.generateId(), packageName = BuildConfig.APPLICATION_ID)

        private val relatedTables = arrayOf(
                Table.User, Table.Category, Table.TimeLimitRule,
                Table.UsedTimeItem, Table.SessionDuration, Table.CategoryApp
        )

        fun load(user: User, database: Database): UserRelatedData = database.runInUnobservedTransaction {
            val categoryEntries = database.category().getCategoriesByChildIdSync(childId = user.id)
            val categories = categoryEntries.map { CategoryRelatedData.load(category = it, database = database) }
            val categoryApps = database.categoryApp().getCategoryAppsByUserIdSync(userId = user.id)

            UserRelatedData(
                    user = user,
                    categories = categories,
                    categoryApps = categoryApps
            ).also { database.registerWeakObserver(relatedTables, WeakReference(it)) }
        }
    }

    val categoryById: Map<String, CategoryRelatedData> by lazy { categories.associateBy { it.category.id } }
    val timeZone: TimeZone by lazy { user.getTimezone() }

    // O(n), but saves memory and index building time
    // additionally a cache
    // notFoundCategoryApp is a workaround because the lru cache does not support null
    private val categoryAppLruCache = object: LruCache<String, CategoryApp>(8) {
        override fun create(key: String): CategoryApp {
            return categoryApps.find { it.packageName == key } ?: notFoundCategoryApp
        }
    }
    fun findCategoryApp(packageName: String): CategoryApp? {
        val item = categoryAppLruCache[packageName]

        // important: strict equality/ same object instance
        if (item === notFoundCategoryApp) {
            return null
        } else {
            return item
        }
    }

    private var userInvalidated = false
    private var categoriesInvalidated = false
    private var rulesInvalidated = false
    private var usedTimesInvalidated = false
    private var sessionDurationsInvalidated = false
    private var categoryAppsInvalidated = false

    private val invalidated
        get() = userInvalidated || categoriesInvalidated || rulesInvalidated || usedTimesInvalidated || sessionDurationsInvalidated || categoryAppsInvalidated

    override fun onInvalidated(tables: Set<Table>) {
        tables.forEach {
            when (it) {
                Table.User -> userInvalidated = true
                Table.Category -> categoriesInvalidated = true
                Table.TimeLimitRule -> rulesInvalidated = true
                Table.UsedTimeItem -> usedTimesInvalidated = true
                Table.SessionDuration -> sessionDurationsInvalidated = true
                Table.CategoryApp -> categoryAppsInvalidated = true
                else -> {/* do nothing */}
            }
        }
    }

    fun update(database: Database) = database.runInUnobservedTransaction {
        if (!invalidated) {
            return@runInUnobservedTransaction this
        }

        val user = if (userInvalidated) database.user().getUserByIdSync(user.id) ?: return@runInUnobservedTransaction null else user
        val categories = if (categoriesInvalidated) {
            val oldCategoriesById = this.categories.associateBy { it.category.id }

            database.category().getCategoriesByChildIdSync(childId = user.id).map { category ->
                val oldItem = oldCategoriesById[category.id]

                oldItem?.update(
                        category = category,
                        database = database,
                        updateDurations = sessionDurationsInvalidated,
                        updateRules = rulesInvalidated,
                        updateTimes = usedTimesInvalidated
                ) ?: CategoryRelatedData.load(
                        category = category,
                        database = database
                )
            }
        } else if (sessionDurationsInvalidated || rulesInvalidated || usedTimesInvalidated) {
            categories.map {
                it.update(
                        category = it.category,
                        database = database,
                        updateDurations = sessionDurationsInvalidated,
                        updateRules = rulesInvalidated,
                        updateTimes = usedTimesInvalidated
                )
            }
        } else {
            categories
        }
        val categoryApps = if (categoryAppsInvalidated) database.categoryApp().getCategoryAppsByUserIdSync(userId = user.id) else categoryApps

        UserRelatedData(
                user = user,
                categories = categories,
                categoryApps = categoryApps
        ).also { database.registerWeakObserver(relatedTables, WeakReference(it)) }
    }
}