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
package io.timelimit.android.data.extensions

import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.derived.CategoryRelatedData

// TODO: remove this
fun List<Category>.sorted(): List<Category> {
    val categoryIds = this.map { it.id }.toSet()

    val sortedCategories = mutableListOf<Category>()
    val childCategories = this.filter { categoryIds.contains(it.parentCategoryId) }.groupBy { it.parentCategoryId }

    this.filterNot { categoryIds.contains(it.parentCategoryId) }.sortedBy { it.sort }.forEach { category ->
        sortedCategories.add(category)

        childCategories[category.id]?.sortedBy { it.sort }?.let { items ->
            sortedCategories.addAll(items)
        }
    }

    return sortedCategories.toList()
}

fun List<CategoryRelatedData>.sortedCategories(): List<CategoryRelatedData> {
    val categoryIds = this.map { it.category.id }.toSet()

    val sortedCategories = mutableListOf<CategoryRelatedData>()
    val childCategories = this.filter { categoryIds.contains(it.category.parentCategoryId) }.groupBy { it.category.parentCategoryId }

    this.filterNot { categoryIds.contains(it.category.parentCategoryId) }.sortedBy { it.category.sort }.forEach { category ->
        sortedCategories.add(category)

        childCategories[category.category.id]?.sortedBy { it.category.sort }?.let { items ->
            sortedCategories.addAll(items)
        }
    }

    return sortedCategories.toList()
}