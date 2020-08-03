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
package io.timelimit.android.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.manage.child.category.CategoryItemLeftPadding
import io.timelimit.android.util.TimeTextUtil

class TimesWidgetService: RemoteViewsService() {
    private val appWidgetManager: AppWidgetManager by lazy { AppWidgetManager.getInstance(this) }

    private val categoriesLive: LiveData<List<TimesWidgetItem>> by lazy {
        TimesWidgetItems.with(DefaultAppLogic.with(this))
    }

    private var categoriesInput: List<TimesWidgetItem> = emptyList()
    private var categoriesCurrent: List<TimesWidgetItem> = categoriesInput

    private val categoriesObserver = Observer<List<TimesWidgetItem>> {
        categoriesInput = it

        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(this, TimesWidgetProvider::class.java))

        appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, android.R.id.list)
    }

    private val factory = object : RemoteViewsFactory {
        override fun onCreate() {
            Threads.mainThreadHandler.post { categoriesLive.observeForever(categoriesObserver) }
        }

        override fun onDestroy() {
            Threads.mainThreadHandler.post { categoriesLive.removeObserver(categoriesObserver) }
        }

        override fun onDataSetChanged() {
            categoriesCurrent = categoriesInput
        }

        override fun getCount(): Int = categoriesCurrent.size

        override fun getViewAt(position: Int): RemoteViews {
            val category = categoriesCurrent[position]
            val result = RemoteViews(packageName, R.layout.widget_times_item)

            result.setTextViewText(R.id.title, category.title)
            result.setTextViewText(
                    R.id.subtitle,
                    if (category.remainingTimeToday == null)
                        getString(R.string.manage_child_category_no_time_limits)
                    else
                        TimeTextUtil.remaining(category.remainingTimeToday.toInt(), this@TimesWidgetService)
            )

            result.setViewPadding(
                    R.id.widgetInnerContainer,
                    // not much space here => / 2
                    CategoryItemLeftPadding.calculate(category.level, this@TimesWidgetService) / 2,
                    0, 0, 0
            )

            result.setViewVisibility(R.id.topPadding, if (position == 0) View.VISIBLE else View.GONE)
            result.setViewVisibility(R.id.bottomPadding, if (position == count - 1) View.VISIBLE else View.GONE)

            return result
        }

        override fun getLoadingView(): RemoteViews? {
            return null
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun getItemId(position: Int): Long {
            return categoriesCurrent[position].hashCode().toLong()
        }

        override fun hasStableIds(): Boolean {
            return true
        }
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = factory
}