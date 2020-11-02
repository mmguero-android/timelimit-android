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

package io.timelimit.android.ui.manage.child.category.specialmode

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import io.timelimit.android.R
import io.timelimit.android.livedata.liveDataFromFunction
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateCategoryDisableLimitsAction
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.ui.main.ActivityViewModel

class SetCategorySpecialModeModel(application: Application): AndroidViewModel(application) {
    private val logic = DefaultAppLogic.with(application)
    private val requestClose = MutableLiveData<Boolean>().apply { value = false }
    private val durationSelectionLive = MutableLiveData<DurationSelection>().apply { value = DurationSelection.SuggestionList }
    private val typeLive = MutableLiveData<Type?>().apply { value = null }
    private var didInit = false
    private val childAndCategoryId = MutableLiveData<Pair<String, String>>()
    private val selfLimitAddModeLive = MutableLiveData<Boolean>().apply { value = false }

    fun now() = logic.realTimeLogic.getCurrentTimeInMillis()

    val nowLive = liveDataFromFunction { now() }
    val hasPremium = logic.fullVersion.shouldProvideFullVersionFunctions

    private val userRelatedData = childAndCategoryId.switchMap { (childId, categoryId) ->
        logic.database.derivedDataDao().getUserRelatedDataLive(childId).map {
            if (it == null) null else it to categoryId
        }
    }

    private val selfLimitAddModeMinTimestamp = userRelatedData.switchMap { data ->
        if (data == null) liveDataFromValue(Long.MAX_VALUE) else {
            val (userRelatedData, categoryId) = data
            val category = userRelatedData.categoryById.get(categoryId)

            if (category == null)
                liveDataFromValue(Long.MAX_VALUE)
            else if (category.category.temporarilyBlocked) {
                if (category.category.temporarilyBlockedEndTime == 0L)
                    liveDataFromValue(Long.MAX_VALUE)
                else
                    nowLive.map { now -> now.coerceAtLeast(category.category.temporarilyBlockedEndTime) }
            } else nowLive
        }
    }

    val minTimestamp = selfLimitAddModeLive.switchMap { selfLimitAddMode ->
        if (selfLimitAddMode) selfLimitAddModeMinTimestamp else nowLive
    }

    val content: LiveData<Content?> = object: MediatorLiveData<Content>() {
        var didLoadUserRelatedData = false

        init {
            addSource(requestClose) { update() }
            addSource(durationSelectionLive) { update() }
            addSource(typeLive) { update() }
            addSource(userRelatedData) { didLoadUserRelatedData = true; update() }
            addSource(selfLimitAddModeLive) { update() }
        }

        fun update() {
            if (requestClose.value == true) {
                value = null; return
            }

            if (!didLoadUserRelatedData) return

            val durationSelection = durationSelectionLive.value!!
            val type = typeLive.value
            val (userRelatedData, categoryId) = userRelatedData.value ?: run { value = null; return }
            val selfLimitAddMode = selfLimitAddModeLive.value ?: return

            val targetCategory = userRelatedData.categoryById[categoryId] ?: run { value = null; return }
            val categoryTitle = targetCategory.category.title

            if (targetCategory.category.temporarilyBlocked && targetCategory.category.temporarilyBlockedEndTime == 0L && selfLimitAddMode) {
                value = null; return
            }

            val screen = if (type == null) Screen.SelectType else when (durationSelection) {
                DurationSelection.SuggestionList -> when (type) {
                    Type.BlockTemporarily -> {
                        if (selfLimitAddMode) SpecialModeDuration.items
                        else listOf(SpecialModeOption.NoEndTimeOption) + SpecialModeDuration.items
                    }
                    Type.DisableLimits -> SpecialModeDuration.items
                }.let { options ->
                    Screen.WithType.SuggestionList(
                            type = type,
                            options = options
                    )
                }
                DurationSelection.Clock -> Screen.WithType.ClockScreen(type = type)
                DurationSelection.Calendar -> Screen.WithType.CalendarScreen(type = type)
            }

            value = Content(
                    categoryTitle = categoryTitle,
                    categoryId = categoryId,
                    childTimezone = userRelatedData.user.timeZone,
                    screen = screen
            )
        }
    }

    fun selectType(type: Type) { typeLive.value = type }
    fun openClockScreen() { durationSelectionLive.value = DurationSelection.Clock }
    fun openCalendarScreen() { durationSelectionLive.value = DurationSelection.Calendar }

    fun goBack(): Boolean = if (durationSelectionLive.value != DurationSelection.SuggestionList) {
        durationSelectionLive.value = DurationSelection.SuggestionList

        true
    } else if (typeLive.value != null) {
        typeLive.value = null

        true
    } else {
        false
    }

    fun applySelection(selection: SpecialModeOption, auth: ActivityViewModel) {
        val content = content.value
        val screen = content?.screen
        val selfLimitAddMode = selfLimitAddModeLive.value ?: return

        if (screen is Screen.WithType) {
            when (selection) {
                is SpecialModeOption.UntilTimeOption -> openClockScreen()
                is SpecialModeOption.UntilDateOption -> openCalendarScreen()
                is SpecialModeOption.Duration -> when (screen.type) {
                    Type.BlockTemporarily -> {
                        val endTime = selection.getTime(
                                currentTimestamp = now(),
                                timezone = content.childTimezone
                        )

                        if (selfLimitAddMode) {
                            val minTime = minTimestamp.value ?: return

                            if (endTime < minTime) {
                                Toast.makeText(getApplication(), R.string.manage_disable_time_limits_toast_time_not_increased_but_child_mode, Toast.LENGTH_SHORT).show()

                                return
                            }
                        }

                        auth.tryDispatchParentAction(
                                action = UpdateCategoryTemporarilyBlockedAction(
                                        categoryId = content.categoryId,
                                        endTime = endTime,
                                        blocked = true
                                ),
                                allowAsChild = selfLimitAddMode
                        )

                        requestClose.value = true
                    }
                    Type.DisableLimits -> {
                        val endTime = selection.getTime(
                                currentTimestamp = now(),
                                timezone = content.childTimezone
                        )

                        auth.tryDispatchParentAction(
                                UpdateCategoryDisableLimitsAction(
                                        categoryId = content.categoryId,
                                        endTime = endTime
                                )
                        )

                        requestClose.value = true
                    }
                }.let {/* require handling all paths */ }
                SpecialModeOption.NoEndTimeOption -> when (screen.type) {
                    Type.BlockTemporarily -> {
                        auth.tryDispatchParentAction(
                                action = UpdateCategoryTemporarilyBlockedAction(
                                        categoryId = content.categoryId,
                                        endTime = null,
                                        blocked = true
                                ),
                                allowAsChild = false
                        )

                        requestClose.value = true
                    }
                    Type.DisableLimits -> throw IllegalArgumentException()
                }.let {/* require handling all paths */ }
            }.let {/* require handling all paths */ }
        }
    }

    fun applySelection(timeInMillis: Long, auth: ActivityViewModel) = applySelection(
            selection = SpecialModeOption.Duration.FixedEndTime(timeInMillis),
            auth = auth
    )

    fun init(childId: String, categoryId: String, selfLimitAddMode: Boolean) {
        if (!didInit) {
            didInit = true

            childAndCategoryId.value = childId to categoryId
            selfLimitAddModeLive.value = selfLimitAddMode
        }
    }

    enum class Type {
        BlockTemporarily,
        DisableLimits
    }

    internal enum class DurationSelection {
        SuggestionList,
        Clock,
        Calendar
    }

    data class Content(
            val categoryTitle: String,
            val categoryId: String,
            val childTimezone: String,
            val screen: Screen
    )

    sealed class Screen {
        object SelectType: Screen()

        sealed class WithType: Screen() {
            abstract val type: Type

            data class ClockScreen(override val type: Type, ): WithType()
            data class CalendarScreen(override val type: Type, ): WithType()

            data class SuggestionList(
                    override val type: Type,
                    val options: List<SpecialModeOption>
            ): WithType()

        }
    }
}