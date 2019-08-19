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
package io.timelimit.android.ui.manage.category.blocked_times

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.Database
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.Category
import io.timelimit.android.data.model.withConfigCopiedToOtherDates
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateCategoryBlockedTimesAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import kotlinx.android.synthetic.main.fragment_blocked_time_areas.*

class BlockedTimeAreasFragment : Fragment(), CopyBlockedTimeAreasDialogFragmentListener {
    companion object {
        fun newInstance(params: ManageCategoryFragmentArgs): BlockedTimeAreasFragment {
            val result = BlockedTimeAreasFragment()
            result.arguments = params.toBundle()
            return result
        }
    }

    private val params: ManageCategoryFragmentArgs by lazy { ManageCategoryFragmentArgs.fromBundle(arguments!!) }
    private val database: Database by lazy { DefaultAppLogic.with(context!!).database }
    private val category: LiveData<Category?> by lazy { database.category().getCategoryByChildIdAndId(params.childId, params.categoryId) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(activity!!) }
    private val items = MutableLiveData<BlockedTimeItems>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        items.value = MinuteOfWeekItems
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_blocked_time_areas, container, false)
    }

    fun updateBlockedTimes(oldMask: ImmutableBitmask, newMask: ImmutableBitmask) {
        if (
                auth.tryDispatchParentAction(
                        UpdateCategoryBlockedTimesAction(
                                categoryId = params.categoryId,
                                blockedTimes = newMask
                        )
                )
        ) {
            Snackbar.make(coordinator, R.string.blocked_time_areas_snackbar_modified, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.generic_undo) {
                        auth.tryDispatchParentAction(
                                UpdateCategoryBlockedTimesAction(
                                        categoryId = params.categoryId,
                                        blockedTimes = oldMask
                                )
                        )
                    }
                    .show()
        }
    }

    override fun onCopyBlockedTimeAreasConfirmed(sourceDay: Int, targetDays: Set<Int>) {
        category.value?.blockedMinutesInWeek?.let { current ->
            updateBlockedTimes(current, current.withConfigCopiedToOtherDates(sourceDay, targetDays))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btn_help.setOnClickListener {
            BlockedTimeAreasHelpDialog().show(fragmentManager!!)
        }

        btn_copy_to_other_days.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                CopyBlockedTimeAreasDialogFragment.newInstance(this@BlockedTimeAreasFragment).show(fragmentManager!!)
            }
        }

        BlockedTimeAreasLogic.init(
                recycler = recycler,
                daySpinner = spinner_day,
                detailedModeCheckbox = detailed_mode,
                requestAuthenticationOrReturnTrue = { auth.requestAuthenticationOrReturnTrue() },
                updateBlockedTimes = { a, b -> updateBlockedTimes(a, b) },
                currentData = category.map { it?.blockedMinutesInWeek },
                lifecycleOwner = this
        )
    }
}
