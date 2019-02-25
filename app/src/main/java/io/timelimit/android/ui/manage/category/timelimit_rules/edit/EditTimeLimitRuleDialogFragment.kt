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
package io.timelimit.android.ui.manage.category.timelimit_rules.edit

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import com.google.android.material.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.data.IdGenerator
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.FragmentEditTimeLimitRuleDialogBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.CreateTimeLimitRuleAction
import io.timelimit.android.sync.actions.DeleteTimeLimitRuleAction
import io.timelimit.android.sync.actions.UpdateTimeLimitRuleAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.view.SelectDayViewHandlers
import io.timelimit.android.ui.view.SelectTimeSpanViewListener
import java.nio.ByteBuffer
import java.util.*


class EditTimeLimitRuleDialogFragment : BottomSheetDialogFragment() {
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

        existingRule = savedInstanceState?.getParcelable(PARAM_EXISTING_RULE)
                ?: arguments?.getParcelable<TimeLimitRule?>(PARAM_EXISTING_RULE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = FragmentEditTimeLimitRuleDialogBinding.inflate(layoutInflater, container, false)
        val listener = targetFragment as EditTimeLimitRuleDialogFragmentListener
        var newRule: TimeLimitRule

        auth.authenticatedUser.observe(this, Observer {
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
                    maximumTimeInMillis = 1000 * 60 * 60 * 5 / 2    // 2,5 (5/2) hours
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
            view.timeSpan.maxDays = affectedDays - 1
            view.affectsMultipleDays = affectedDays >= 2
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

            override fun onSaveRule() {
                if (existingRule != null) {
                    if (existingRule != newRule) {
                        if (!auth.tryDispatchParentAction(
                                        UpdateTimeLimitRuleAction(
                                                ruleId = newRule.id,
                                                maximumTimeInMillis = newRule.maximumTimeInMillis,
                                                dayMask = newRule.dayMask,
                                                applyToExtraTimeUsage = newRule.applyToExtraTimeUsage
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
        }

        if (existingRule != null) {
            DefaultAppLogic.with(context!!).database.timeLimitRules()
                    .getTimeLimitRuleByIdLive(existingRule!!.id).observe(this, Observer {
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
}

interface Handlers {
    fun updateApplyToExtraTime(apply: Boolean)
    fun onSaveRule()
    fun onDeleteRule()
}

interface EditTimeLimitRuleDialogFragmentListener {
    fun notifyRuleCreated()
    fun notifyRuleDeleted(oldRule: TimeLimitRule)
    fun notifyRuleUpdated(oldRule: TimeLimitRule, newRule: TimeLimitRule)
}
