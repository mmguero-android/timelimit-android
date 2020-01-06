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
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.timelimit.android.R
import io.timelimit.android.logic.DefaultAppLogic

class TimesWidgetProvider: AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)

        // always start the app logic
        DefaultAppLogic.with(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_times)

            views.setRemoteAdapter(android.R.id.list, Intent(context, TimesWidgetService::class.java))
            views.setEmptyView(android.R.id.list, android.R.id.empty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}