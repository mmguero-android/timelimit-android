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
package io.timelimit.android.ui.manage.category.settings

import android.widget.CheckBox
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.CategoryTimeWarnings
import io.timelimit.android.databinding.CategoryTimeWarningsViewBinding
import io.timelimit.android.sync.actions.UpdateCategoryTimeWarningsAction
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.util.TimeTextUtil

object CategoryTimeWarningView {
    fun bind(
            view: CategoryTimeWarningsViewBinding,
            lifecycleOwner: LifecycleOwner,
            categoryLive: LiveData<Category?>,
            auth: ActivityViewModel,
            fragmentManager: FragmentManager
    ) {
        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.time_warning_title,
                    text = R.string.time_warning_desc
            ).show(fragmentManager)
        }

        view.linearLayout.removeAllViews()

        val durationToCheckbox = mutableMapOf<Long, CheckBox>()

        CategoryTimeWarnings.durations.sorted().forEach { duration ->
            CheckBox(view.root.context).let { checkbox ->
                checkbox.text = TimeTextUtil.time(duration.toInt(), view.root.context)

                view.linearLayout.addView(checkbox)
                durationToCheckbox[duration] = checkbox
            }
        }

        categoryLive.observe(lifecycleOwner, Observer { category ->
            durationToCheckbox.entries.forEach { (duration, checkbox) ->
                checkbox.setOnCheckedChangeListener { _, _ ->  }

                val flag = (1 shl CategoryTimeWarnings.durationToBitIndex[duration]!!)
                val enable = (category?.timeWarnings ?: 0) and flag != 0
                checkbox.isChecked = enable

                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != enable && category != null) {
                        if (auth.tryDispatchParentAction(
                                        UpdateCategoryTimeWarningsAction(
                                                categoryId = category.id,
                                                enable = isChecked,
                                                flags = flag
                                        )
                                )) {
                            // it worked
                        } else {
                            checkbox.isChecked = enable
                        }
                    }
                }
            }
        })
    }
}