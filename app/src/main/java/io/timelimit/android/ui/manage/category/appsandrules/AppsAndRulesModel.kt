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

package io.timelimit.android.ui.manage.category.appsandrules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.data.extensions.getDateLive
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import java.util.*

class AppsAndRulesModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val database = logic.database
    private val userIdLive = MutableLiveData<String>()
    private val categoryIdLive = MutableLiveData<String>()
    private var didInit = false

    private val userDate = userIdLive.switchMap { userId ->
        database.user().getUserByIdLive(userId).getDateLive(logic.realTimeLogic)
    }

    val firstDayOfWeekAndUsedTimes = userDate.switchMap { date ->
        val firstDayOfWeekAsEpochDay = date.dayOfEpoch - date.dayOfWeek

        categoryIdLive.switchMap { categoryId ->
            database.usedTimes().getUsedTimesOfWeek(
                    categoryId = categoryId,
                    firstDayOfWeekAsEpochDay = firstDayOfWeekAsEpochDay
            )
        }.map { usedTimes ->
            firstDayOfWeekAsEpochDay to usedTimes
        }
    }

    private val allRules = categoryIdLive.switchMap { categoryId ->
        database.timeLimitRules().getTimeLimitRulesByCategory(categoryId)
    }.map { rules ->
        rules.sortedBy { it.dayMask }.map { AppAndRuleItem.RuleEntry(it) }
    }

    private val hasHiddenRuleIntro = database.config().wereHintsShown(HintsToShow.TIME_LIMIT_RULE_INTRODUCTION)

    val fullRuleScreenContent = allRules.switchMap { allRules ->
        val baseList = allRules + listOf(AppAndRuleItem.AddRuleItem)
        hasHiddenRuleIntro.map { hasHiddenRuleIntro ->
            if (hasHiddenRuleIntro) {
                baseList
            } else {
                listOf(AppAndRuleItem.RulesIntro) + baseList
            }
        }
    }

    private val installedApps = database.app().getAllApps()

    private val appsOfThisCategory = categoryIdLive.switchMap { categoryId -> database.categoryApp().getCategoryApps(categoryId) }

    private val appsOfCategoryWithNames = installedApps.switchMap { allApps ->
        appsOfThisCategory.map { apps ->
            apps.map { categoryApp ->
                categoryApp to allApps.find { app -> app.packageName == categoryApp.packageNameWithoutActivityName }
            }
        }
    }

    private val appEntries = appsOfCategoryWithNames.map { apps ->
        apps.map { (app, appEntry) ->
            if (appEntry != null) {
                AppAndRuleItem.AppEntry(appEntry.title, app.packageName, app.packageNameWithoutActivityName)
            } else {
                AppAndRuleItem.AppEntry("app not found", app.packageName, app.packageNameWithoutActivityName)
            }
        }.sortedBy { it.title.toLowerCase(Locale.US) }
    }

    val fullAppScreenContent = appEntries.map { it + listOf(AppAndRuleItem.AddAppItem) }

    fun init(userId: String, categoryId: String) {
        if (didInit) return; didInit = true

        userIdLive.value = userId
        categoryIdLive.value = categoryId
    }
}