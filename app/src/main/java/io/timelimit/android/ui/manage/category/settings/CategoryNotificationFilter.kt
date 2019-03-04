package io.timelimit.android.ui.manage.category.settings

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.data.model.Category
import io.timelimit.android.databinding.CategoryNotificationFilterBinding
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.sync.actions.UpdateCategoryBlockAllNotificationsAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.payment.RequiresPurchaseDialogFragment

object CategoryNotificationFilter {
    fun bind(
            view: CategoryNotificationFilterBinding,
            auth: ActivityViewModel,
            categoryLive: LiveData<Category?>,
            lifecycleOwner: LifecycleOwner,
            fragmentManager: FragmentManager
    ) {
        val premium = auth.logic.fullVersion.shouldProvideFullVersionFunctions

        mergeLiveData(categoryLive, premium).observe(lifecycleOwner, Observer { (category, hasPremium) ->
            val shouldBeChecked = category?.blockAllNotifications ?: false

            view.checkbox.setOnCheckedChangeListener { _, _ ->  }
            view.checkbox.isChecked = shouldBeChecked
            view.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != shouldBeChecked) {
                    if (isChecked && (hasPremium != true)) {
                        RequiresPurchaseDialogFragment().show(fragmentManager)
                        view.checkbox.isChecked = shouldBeChecked
                    } else {
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
                            view.checkbox.isChecked = shouldBeChecked
                        }
                    }
                }
            }
        })
    }
}