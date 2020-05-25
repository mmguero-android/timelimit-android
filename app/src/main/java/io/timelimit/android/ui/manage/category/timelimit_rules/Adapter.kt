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
package io.timelimit.android.ui.manage.category.timelimit_rules

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.data.model.TimeLimitRule
import io.timelimit.android.data.model.UsedTimeItem
import io.timelimit.android.databinding.AddItemViewBinding
import io.timelimit.android.databinding.FragmentCategoryTimeLimitRuleItemBinding
import io.timelimit.android.databinding.TimeLimitRuleIntroductionBinding
import io.timelimit.android.extensions.MinuteOfDay
import io.timelimit.android.util.JoinUtil
import io.timelimit.android.util.TimeTextUtil
import kotlin.properties.Delegates

class Adapter: RecyclerView.Adapter<ViewHolder>() {
    companion object {
        private const val TYPE_ITEM = 1
        private const val TYPE_ADD = 2
        private const val TYPE_INTRO = 3
    }

    var data: List<TimeLimitRuleItem> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var usedTimes: List<UsedTimeItem> by Delegates.observable(emptyList()) { _, _, _ -> notifyDataSetChanged() }
    var epochDayOfStartOfWeek: Int by Delegates.observable(0) { _, _, _ -> notifyDataSetChanged() }
    var handlers: Handlers? = null

    init {
        setHasStableIds(true)
    }

    private fun getItem(position: Int): TimeLimitRuleItem = data[position]

    override fun getItemId(position: Int): Long {
        val item = getItem(position)

        return if (item is TimeLimitRuleRuleItem) {
            item.rule.id.hashCode()
        } else {
            item.hashCode()
        }.toLong()
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        AddTimeLimitRuleItem -> TYPE_ADD
        is TimeLimitRuleRuleItem -> TYPE_ITEM
        TimeLimitRuleIntroductionItem -> TYPE_INTRO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        TYPE_ITEM -> {
            ItemViewHolder(
                    FragmentCategoryTimeLimitRuleItemBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    )
            )
        }
        TYPE_ADD -> {
            ViewHolder(
                    AddItemViewBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    ).apply {
                        label = parent.context.getString(R.string.category_time_limit_rule_dialog_new)
                        root.setOnClickListener { handlers?.onAddTimeLimitRuleClicked() }
                    }.root
            )
        }
        TYPE_INTRO -> {
            ViewHolder(
                    TimeLimitRuleIntroductionBinding.inflate(
                            LayoutInflater.from(parent.context),
                            parent,
                            false
                    ).root
            )
        }
        else -> throw IllegalStateException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        when (item) {
            AddTimeLimitRuleItem -> {
                // nothing to do
            }
            is TimeLimitRuleIntroductionItem -> {
                // nothing to do
            }
            is TimeLimitRuleRuleItem -> {
                val rule = item.rule
                val binding = (holder as ItemViewHolder).view
                val context = binding.root.context
                val dayNames = binding.root.resources.getStringArray(R.array.days_of_week_array)
                val usedTime = usedTimes.filter { usedTime ->
                    val dayOfWeek = usedTime.dayOfEpoch - epochDayOfStartOfWeek
                    usedTime.startTimeOfDay == rule.startMinuteOfDay && usedTime.endTimeOfDay == rule.endMinuteOfDay &&
                            (rule.dayMask.toInt() and (1 shl dayOfWeek) != 0)
                }.map { it.usedMillis }.sum().toInt()

                binding.maxTimeString = TimeTextUtil.time(rule.maximumTimeInMillis, context)
                binding.usageAsText = TimeTextUtil.used(usedTime, context)
                binding.usageProgressInPercent = if (rule.maximumTimeInMillis > 0)
                    (usedTime * 100 / rule.maximumTimeInMillis)
                else
                    100
                binding.daysString = JoinUtil.join(
                        dayNames.filterIndexed { index, _ -> (rule.dayMask.toInt() and (1 shl index)) != 0 },
                        context
                )
                binding.timeAreaString = if (rule.appliesToWholeDay)
                    null
                else
                    context.getString(
                            R.string.category_time_limit_rules_time_area,
                            MinuteOfDay.format(rule.startMinuteOfDay),
                            MinuteOfDay.format(rule.endMinuteOfDay)
                    )
                binding.appliesToExtraTime = rule.applyToExtraTimeUsage
                binding.sessionLimitString = if (rule.sessionDurationLimitEnabled)
                    context.getString(
                            R.string.category_time_limit_rules_session_limit,
                            TimeTextUtil.time(rule.sessionPauseMilliseconds, context),
                            TimeTextUtil.time(rule.sessionDurationMilliseconds, context)
                    )
                else
                    null

                binding.card.setOnClickListener { handlers?.onTimeLimitRuleClicked(rule) }

                binding.executePendingBindings()
            }
        }.let { /* require handling all paths */ }
    }
}

open class ViewHolder(view: View): RecyclerView.ViewHolder(view)
class ItemViewHolder(val view: FragmentCategoryTimeLimitRuleItemBinding): ViewHolder(view.root)

interface Handlers {
    fun onTimeLimitRuleClicked(rule: TimeLimitRule)
    fun onAddTimeLimitRuleClicked()
}
