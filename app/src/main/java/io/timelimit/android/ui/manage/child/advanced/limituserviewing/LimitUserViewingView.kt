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

package io.timelimit.android.ui.manage.child.advanced.limituserviewing

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.data.model.User
import io.timelimit.android.data.model.UserFlags
import io.timelimit.android.databinding.LimitUserViewingViewBinding
import io.timelimit.android.livedata.ignoreUnchanged
import io.timelimit.android.livedata.map
import io.timelimit.android.sync.actions.UpdateUserFlagsAction
import io.timelimit.android.ui.help.HelpDialogFragment
import io.timelimit.android.ui.main.ActivityViewModel

object LimitUserViewingView {
    fun bind(
            view: LimitUserViewingViewBinding,
            auth: ActivityViewModel,
            lifecycleOwner: LifecycleOwner,
            fragmentManager: FragmentManager,
            userEntry: LiveData<User?>,
            userId: String
    ) {
        view.titleView.setOnClickListener {
            HelpDialogFragment.newInstance(
                    title = R.string.limit_user_viewing_title,
                    text = R.string.limit_user_viewing_help
            ).show(fragmentManager)
        }

        userEntry.map { it?.restrictViewingToParents ?: false }.ignoreUnchanged().observe(lifecycleOwner, Observer { checked ->
            view.enableSwitch.setOnCheckedChangeListener { buttonView, isChecked -> /* ignore */ }
            view.enableSwitch.isChecked = checked
            view.enableSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
                if (isChecked != checked) {
                    if (
                            !auth.tryDispatchParentAction(
                                    UpdateUserFlagsAction(
                                            userId = userId,
                                            modifiedBits = UserFlags.RESTRICT_VIEWING_TO_PARENTS,
                                            newValues = if (isChecked) UserFlags.RESTRICT_VIEWING_TO_PARENTS else 0
                                    )
                            )
                    ) {
                        view.enableSwitch.isChecked = checked
                    }
                }
            }
        })
    }
}