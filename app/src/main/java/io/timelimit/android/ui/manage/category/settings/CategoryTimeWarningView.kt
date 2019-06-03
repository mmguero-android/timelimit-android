package io.timelimit.android.ui.manage.category.settings

import android.widget.CheckBox
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.CategoryTimeWarnings
import io.timelimit.android.databinding.CategoryTimeWarningsViewBinding
import io.timelimit.android.sync.actions.UpdateCategoryTimeWarningsAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.util.TimeTextUtil

object CategoryTimeWarningView {
    fun bind(
            view: CategoryTimeWarningsViewBinding,
            lifecycleOwner: LifecycleOwner,
            categoryLive: LiveData<Category?>,
            auth: ActivityViewModel
    ) {
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