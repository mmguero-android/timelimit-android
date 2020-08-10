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

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.data.model.Category
import io.timelimit.android.databinding.CategoryNotificationFilterBinding
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.sync.actions.UpdateCategoryBlockAllNotificationsAction
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.payment.RequiresPurchaseDialogFragment

object CategoryNotificationFilter {
    fun bind(
            view: CategoryNotificationFilterBinding,
            auth: ActivityViewModel,
            categoryLive: LiveData<Category?>,
            lifecycleOwner: LifecycleOwner,
            fragmentManager: FragmentManager,
            childId: String
    ) {
        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.category_notification_filter_title,
                    text = R.string.category_notification_filter_text
            ).show(fragmentManager)
        }

        val premium = auth.logic.fullVersion.shouldProvideFullVersionFunctions

        mergeLiveData(categoryLive, premium).observe(lifecycleOwner, Observer { (category, hasPremium) ->
            val shouldBeChecked = category?.blockAllNotifications ?: false

            view.checkbox.setOnCheckedChangeListener { _, _ ->  }
            view.checkbox.isChecked = shouldBeChecked
            view.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != shouldBeChecked) {
                    if (isChecked) {
                        if (hasPremium != true) {
                            RequiresPurchaseDialogFragment().show(fragmentManager)
                        } else if (auth.requestAuthenticationOrReturnTrueAllowChild(childId) && category != null) {
                            EnableNotificationFilterDialogFragment.newInstance(
                                    childId = childId,
                                    categoryId = category.id
                            ).show(fragmentManager)
                        }

                        view.checkbox.isChecked = false
                    } else /* not isChecked */ {
                        if (
                                category != null &&
                                auth.tryDispatchParentAction(
                                        UpdateCategoryBlockAllNotificationsAction(
                                                categoryId = category.id,
                                                blocked = isChecked
                                        )
                                )
                        ) {
                            // ok
                        } else {
                            view.checkbox.isChecked = true
                        }
                    }
                }
            }
        })
    }
}