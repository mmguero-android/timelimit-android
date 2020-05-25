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
package io.timelimit.android.ui.manage.category.timelimit_rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.Database
import io.timelimit.android.data.extensions.getDateLive
import io.timelimit.android.data.model.HintsToShow
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.CreateTimeLimitRuleAction
import io.timelimit.android.sync.actions.UpdateTimeLimitRuleAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.manage.category.timelimit_rules.edit.EditTimeLimitRuleDialogFragment
import io.timelimit.android.ui.manage.category.timelimit_rules.edit.EditTimeLimitRuleDialogFragmentListener
import kotlinx.android.synthetic.main.fragment_category_time_limit_rules.*

class CategoryTimeLimitRulesFragment : Fragment(), EditTimeLimitRuleDialogFragmentListener {
    companion object {
        fun newInstance(params: ManageCategoryFragmentArgs): CategoryTimeLimitRulesFragment {
            val result = CategoryTimeLimitRulesFragment()
            result.arguments = params.toBundle()
            return result
        }
    }

    private val logic: AppLogic by lazy { DefaultAppLogic.with(context!!) }
    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(arguments!!) }
    private val database: Database by lazy { logic.database }
    private val rules: LiveData<List<TimeLimitRule>> by lazy { database.timeLimitRules().getTimeLimitRulesByCategory(params.categoryId) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_category_time_limit_rules, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = Adapter()

        recycler.layoutManager = LinearLayoutManager(context!!)
        recycler.adapter = adapter

        val userDate = database.user().getUserByIdLive(params.childId).getDateLive(logic.realTimeLogic)

        userDate.switchMap { date ->
            val firstDayOfWeekAsEpochDay = date.dayOfEpoch - date.dayOfWeek

            database.usedTimes().getUsedTimesOfWeek(
                    categoryId = params.categoryId,
                    firstDayOfWeekAsEpochDay = firstDayOfWeekAsEpochDay
            ).map { res ->
                firstDayOfWeekAsEpochDay to res
            }
        }.observe(viewLifecycleOwner, Observer {
            adapter.epochDayOfStartOfWeek = it.first
            adapter.usedTimes = it.second
        })

        val hasHiddenIntro = database.config().wereHintsShown(HintsToShow.TIME_LIMIT_RULE_INTRODUCTION)

        rules.map { rules ->
            rules.sortedBy { it.dayMask }.map { TimeLimitRuleRuleItem(it) }
        }.switchMap {
            val baseList = it + listOf(AddTimeLimitRuleItem)

            hasHiddenIntro.map { hasHiddenIntro ->
                if (hasHiddenIntro) {
                    baseList
                } else {
                    listOf(TimeLimitRuleIntroductionItem) + baseList
                }
            }
        }.observe(viewLifecycleOwner, Observer {
            adapter.data = it
        })

        adapter.handlers = object: Handlers {
            override fun onTimeLimitRuleClicked(rule: TimeLimitRule) {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    EditTimeLimitRuleDialogFragment.newInstance(rule, this@CategoryTimeLimitRulesFragment).show(fragmentManager!!)
                }
            }

            override fun onAddTimeLimitRuleClicked() {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    EditTimeLimitRuleDialogFragment.newInstance(params.categoryId, this@CategoryTimeLimitRulesFragment).show(fragmentManager!!)
                }
            }
        }

        ItemTouchHelper(object: ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val index = viewHolder.adapterPosition
                val item = if (index == RecyclerView.NO_POSITION) null else adapter.data[index]

                if (item == TimeLimitRuleIntroductionItem) {
                    return makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.END or ItemTouchHelper.START) or
                            makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.END or ItemTouchHelper.START)
                } else {
                    return 0
                }
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = throw IllegalStateException()

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val database = logic.database

                Threads.database.submit {
                    database.config().setHintsShownSync(HintsToShow.TIME_LIMIT_RULE_INTRODUCTION)
                }
            }
        }).attachToRecyclerView(recycler)
    }

    override fun notifyRuleCreated() {
        Snackbar.make(view!!, R.string.category_time_limit_rules_snackbar_created, Snackbar.LENGTH_SHORT)
                .show()
    }

    override fun notifyRuleDeleted(oldRule: TimeLimitRule) {
        Snackbar.make(view!!, R.string.category_time_limit_rules_snackbar_deleted, Snackbar.LENGTH_SHORT)
                .setAction(R.string.generic_undo) {
                    auth.tryDispatchParentAction(
                            CreateTimeLimitRuleAction(
                                    rule = oldRule
                            )
                    )
                }
                .show()
    }

    override fun notifyRuleUpdated(oldRule: TimeLimitRule, newRule: TimeLimitRule) {
        Snackbar.make(view!!, R.string.category_time_limit_rules_snackbar_updated, Snackbar.LENGTH_SHORT)
                .setAction(R.string.generic_undo) {
                    auth.tryDispatchParentAction(
                            UpdateTimeLimitRuleAction(
                                    ruleId = oldRule.id,
                                    applyToExtraTimeUsage = oldRule.applyToExtraTimeUsage,
                                    maximumTimeInMillis = oldRule.maximumTimeInMillis,
                                    dayMask = oldRule.dayMask,
                                    start = oldRule.startMinuteOfDay,
                                    end = oldRule.endMinuteOfDay,
                                    sessionDurationMilliseconds = oldRule.sessionDurationMilliseconds,
                                    sessionPauseMilliseconds = oldRule.sessionPauseMilliseconds
                            )
                    )
                }
                .show()
    }
}
