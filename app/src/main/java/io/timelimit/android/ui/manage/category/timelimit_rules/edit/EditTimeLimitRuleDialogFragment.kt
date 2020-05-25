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
package io.timelimit.android.ui.manage.category.timelimit_rules.edit

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.FragmentEditTimeLimitRuleDialogBinding
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.CreateTimeLimitRuleAction
import io.timelimit.android.sync.actions.DeleteTimeLimitRuleAction
import io.timelimit.android.sync.actions.UpdateTimeLimitRuleAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.mustread.MustReadFragment
import io.timelimit.android.ui.view.SelectDayViewHandlers
import io.timelimit.android.ui.view.SelectTimeSpanViewListener
import io.timelimit.android.util.TimeTextUtil
import java.nio.ByteBuffer
import java.util.*


class EditTimeLimitRuleDialogFragment : BottomSheetDialogFragment(), DurationPickerDialogFragmentListener {
    companion object {
        private const val PARAM_EXISTING_RULE = "a"
        private const val PARAM_CATEGORY_ID = "b"
        private const val DIALOG_TAG = "t"
        private const val STATE_RULE = "c"

        fun newInstance(existingRule: TimeLimitRule, listener: Fragment) = EditTimeLimitRuleDialogFragment()
                .apply {
                    arguments = Bundle().apply {
                        putParcelable(PARAM_EXISTING_RULE, existingRule)
                    }

                    setTargetFragment(listener, 0)
                }

        fun newInstance(categoryId: String, listener: Fragment) = EditTimeLimitRuleDialogFragment()
                .apply {
                    arguments = Bundle().apply {
                        putString(PARAM_CATEGORY_ID, categoryId)
                    }

                    setTargetFragment(listener, 0)
                }
    }

    var existingRule: TimeLimitRule? = null
    var savedNewRule: TimeLimitRule? = null
    lateinit var newRule: TimeLimitRule
    lateinit var view: FragmentEditTimeLimitRuleDialogBinding

    private val categoryId: String by lazy {
        if (existingRule != null) {
            existingRule!!.categoryId
        } else {
            arguments!!.getString(PARAM_CATEGORY_ID)!!
        }
    }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val database = DefaultAppLogic.with(context!!).database

            runAsync {
                val wasShown = database.config().wereHintsShown(HintsToShow.TIMELIMIT_RULE_MUSTREAD).waitForNonNullValue()

                if (!wasShown) {
                    MustReadFragment.newInstance(io.timelimit.android.R.string.must_read_timelimit_rules).show(fragmentManager!!)

                    Threads.database.execute {
                        database.config().setHintsShownSync(HintsToShow.TIMELIMIT_RULE_MUSTREAD)
                    }
                }
            }
        }

        existingRule = savedInstanceState?.getParcelable(PARAM_EXISTING_RULE)
                ?: arguments?.getParcelable<TimeLimitRule?>(PARAM_EXISTING_RULE)
    }

    fun bindRule() {
        savedNewRule = newRule

        view.daySelection.selectedDays = BitSet.valueOf(
                ByteBuffer.allocate(1).put(newRule.dayMask).apply {
                    position(0)
                }
        )
        view.applyToExtraTime = newRule.applyToExtraTimeUsage
        view.timeSpan.timeInMillis = newRule.maximumTimeInMillis.toLong()

        val affectedDays = Math.max(0, (0..6).map { (newRule.dayMask.toInt() shr it) and 1 }.sum())
        view.timeSpan.maxDays = Math.max(0, affectedDays - 1)   // max prevents crash
        view.affectsMultipleDays = affectedDays >= 2

        view.applyToWholeDay = newRule.appliesToWholeDay
        view.startTime = MinuteOfDay.format(newRule.startMinuteOfDay)
        view.endTime = MinuteOfDay.format(newRule.endMinuteOfDay)

        view.enableSessionDurationLimit = newRule.sessionDurationLimitEnabled
        view.sessionBreakText = TimeTextUtil.minutes(newRule.sessionPauseMilliseconds / (1000 * 60), context!!)
        view.sessionLengthText = TimeTextUtil.minutes(newRule.sessionDurationMilliseconds / (1000 * 60), context!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val listener = targetFragment as EditTimeLimitRuleDialogFragmentListener
        val database = DefaultAppLogic.with(context!!).database

        view = FragmentEditTimeLimitRuleDialogBinding.inflate(layoutInflater, container, false)

        auth.authenticatedUser.observe(viewLifecycleOwner, Observer {
            if (it == null || it.second.type != UserType.Parent) {
                dismissAllowingStateLoss()
            }
        })

        if (existingRule == null) {
            view.isNewRule = true

            newRule = TimeLimitRule(
                    id = IdGenerator.generateId(),
                    categoryId = categoryId,
                    applyToExtraTimeUsage = false,
                    dayMask = 0,
                    maximumTimeInMillis = 1000 * 60 * 60 * 5 / 2,   // 2,5 (5/2) hours
                    startMinuteOfDay = TimeLimitRule.MIN_START_MINUTE,
                    endMinuteOfDay = TimeLimitRule.MAX_END_MINUTE,
                    sessionPauseMilliseconds = 0,
                    sessionDurationMilliseconds = 0
            )
        } else {
            view.isNewRule = false

            newRule = existingRule!!
        }

        run {
            val restoredRule = savedInstanceState?.getParcelable<TimeLimitRule>(STATE_RULE)

            if (restoredRule != null) {
                newRule = restoredRule
            }
        }

        bindRule()
        view.daySelection.handlers = object: SelectDayViewHandlers {
            override fun updateDayChecked(day: Int) {
                newRule = newRule.copy(
                        dayMask = (newRule.dayMask.toInt() xor (1 shl day)).toByte()
                )

                bindRule()
            }
        }

        view.handlers = object: Handlers {
            override fun updateApplyToExtraTime(apply: Boolean) {
                newRule = newRule.copy(
                        applyToExtraTimeUsage = apply
                )

                bindRule()
            }

            override fun updateApplyToWholeDay(apply: Boolean) {
                if (apply) {
                    newRule = newRule.copy(
                            startMinuteOfDay = TimeLimitRule.MIN_START_MINUTE,
                            endMinuteOfDay = TimeLimitRule.MAX_END_MINUTE
                    )
                } else {
                    newRule = newRule.copy(
                            startMinuteOfDay = 10 * 60,
                            endMinuteOfDay = 16 * 60
                    )
                }

                bindRule()
            }

            override fun updateStartTime() {
                TimePickerDialogFragment.newInstance(
                        editTimeLimitRuleDialogFragment = this@EditTimeLimitRuleDialogFragment,
                        index = 0,
                        startMinuteOfDay = newRule.startMinuteOfDay
                ).show(parentFragmentManager)
            }

            override fun updateEndTime() {
                TimePickerDialogFragment.newInstance(
                        editTimeLimitRuleDialogFragment = this@EditTimeLimitRuleDialogFragment,
                        index = 1,
                        startMinuteOfDay = newRule.endMinuteOfDay
                ).show(parentFragmentManager)
            }

            override fun updateSessionDurationLimit(enable: Boolean) {
                if (enable) {
                    newRule = newRule.copy(
                            sessionDurationMilliseconds = 1000 * 60 * 30,
                            sessionPauseMilliseconds = 1000 * 60 * 10
                    )
                } else {
                    newRule = newRule.copy(
                            sessionDurationMilliseconds = 0,
                            sessionPauseMilliseconds = 0
                    )
                }

                bindRule()
            }

            override fun updateSessionLength() {
                DurationPickerDialogFragment.newInstance(
                        titleRes = R.string.category_time_limit_rules_session_limit_duration,
                        index = 0,
                        target = this@EditTimeLimitRuleDialogFragment,
                        startTimeInMillis = newRule.sessionDurationMilliseconds
                ).show(parentFragmentManager)
            }

            override fun updateSessionBreak() {
                DurationPickerDialogFragment.newInstance(
                        titleRes = R.string.category_time_limit_rules_session_limit_pause,
                        index = 1,
                        target = this@EditTimeLimitRuleDialogFragment,
                        startTimeInMillis = newRule.sessionPauseMilliseconds
                ).show(parentFragmentManager)
            }

            override fun onSaveRule() {
                view.timeSpan.clearNumberPickerFocus()

                if (existingRule != null) {
                    if (existingRule != newRule) {
                        if (!auth.tryDispatchParentAction(
                                        UpdateTimeLimitRuleAction(
                                                ruleId = newRule.id,
                                                maximumTimeInMillis = newRule.maximumTimeInMillis,
                                                dayMask = newRule.dayMask,
                                                applyToExtraTimeUsage = newRule.applyToExtraTimeUsage,
                                                start = newRule.startMinuteOfDay,
                                                end = newRule.endMinuteOfDay,
                                                sessionDurationMilliseconds = newRule.sessionDurationMilliseconds,
                                                sessionPauseMilliseconds = newRule.sessionPauseMilliseconds
                                        )
                                )) {
                            return
                        }
                    }

                    listener.notifyRuleUpdated(existingRule!!, newRule)
                } else {
                    if (!auth.tryDispatchParentAction(
                                    CreateTimeLimitRuleAction(
                                            rule = newRule
                                    )
                            )) {
                        return
                    }

                    listener.notifyRuleCreated()
                }

                dismissAllowingStateLoss()
            }

            override fun onDeleteRule() {
                if (!auth.tryDispatchParentAction(
                                DeleteTimeLimitRuleAction(
                                        ruleId = existingRule!!.id
                                )
                        )) {
                    return
                }

                listener.notifyRuleDeleted(existingRule!!)

                dismissAllowingStateLoss()
            }
        }

        view.timeSpan.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) {
                if (newTimeInMillis.toInt() != newRule.maximumTimeInMillis) {
                    newRule = newRule.copy(maximumTimeInMillis = newTimeInMillis.toInt())

                    bindRule()
                }
            }

            override fun setEnablePickerMode(enable: Boolean) {
                Threads.database.execute {
                    database.config().setEnableAlternativeDurationSelectionSync(enable)
                }
            }
        }

        database.config().getEnableAlternativeDurationSelectionAsync().observe(viewLifecycleOwner, Observer {
            view.timeSpan.enablePickerMode(it)
        })

        if (existingRule != null) {
            database.timeLimitRules()
                    .getTimeLimitRuleByIdLive(existingRule!!.id).observe(viewLifecycleOwner, Observer {
                        if (it == null) {
                            // rule was deleted
                            dismissAllowingStateLoss()
                        } else {
                            if (it != existingRule) {
                                existingRule = it
                                newRule = it

                                bindRule()
                            }
                        }
                    })
        }

        return view.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        // from https://stackoverflow.com/a/43602359
        dialog.setOnShowListener {
            BottomSheetBehavior.from(
                    dialog.findViewById<View>(R.id.design_bottom_sheet)
            ).setState(BottomSheetBehavior.STATE_EXPANDED)
        }

        return dialog
    }

    fun show(manager: FragmentManager) {
        showSafe(manager, DIALOG_TAG)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val rule = savedNewRule

        if (rule != null) {
            outState.putParcelable(STATE_RULE, rule)
        }

        existingRule.let { existingRule ->
            if (existingRule != null) {
                outState.putParcelable(PARAM_EXISTING_RULE, existingRule)
            }
        }
    }

    fun handleTimePickerResult(index: Int, minuteOfDay: Int) {
        if (!MinuteOfDay.isValid(minuteOfDay)) {
            Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()

            return
        }

        if (index == 0) {
            // start minute

            if (minuteOfDay > newRule.endMinuteOfDay) {
                Toast.makeText(context, R.string.category_time_limit_rules_invalid_range, Toast.LENGTH_SHORT).show()
            } else {
                newRule = newRule.copy(startMinuteOfDay = minuteOfDay)
                bindRule()
            }
        } else if (index == 1) {
            // end minute

            if (minuteOfDay < newRule.startMinuteOfDay) {
                Toast.makeText(context, R.string.category_time_limit_rules_invalid_range, Toast.LENGTH_SHORT).show()
            } else {
                newRule = newRule.copy(endMinuteOfDay = minuteOfDay)
                bindRule()
            }
        } else {
            Toast.makeText(context, R.string.error_general, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDurationSelected(durationInMillis: Int, index: Int) {
        if (index == 0) {
            newRule = newRule.copy(
                    sessionDurationMilliseconds = durationInMillis
            )

            bindRule()
        } else if (index == 1) {
            newRule = newRule.copy(
                    sessionPauseMilliseconds = durationInMillis
            )

            bindRule()
        } else {
            throw IllegalArgumentException()
        }
    }
}

interface Handlers {
    fun updateApplyToExtraTime(apply: Boolean)
    fun updateApplyToWholeDay(apply: Boolean)
    fun updateStartTime()
    fun updateEndTime()
    fun updateSessionDurationLimit(enable: Boolean)
    fun updateSessionLength()
    fun updateSessionBreak()
    fun onSaveRule()
    fun onDeleteRule()
}

interface EditTimeLimitRuleDialogFragmentListener {
    fun notifyRuleCreated()
    fun notifyRuleDeleted(oldRule: TimeLimitRule)
    fun notifyRuleUpdated(oldRule: TimeLimitRule, newRule: TimeLimitRule)
}
