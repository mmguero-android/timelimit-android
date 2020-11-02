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
package io.timelimit.android.ui.manage.child.advanced.manageblocktemporarily

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.timelimit.android.R
import io.timelimit.android.data.RoomDatabase
import io.timelimit.android.data.model.UserType
import io.timelimit.android.databinding.BlockTemporarilyDialogBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.RealTime
import io.timelimit.android.sync.actions.UpdateCategoryTemporarilyBlockedAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.manage.child.category.specialmode.*
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId

class BlockTemporarilyDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "BlockTemporarilyDialogFragment"
        private const val EXTRA_CATEGORY_ID = "categoryId"
        private const val EXTRA_CHILD_ID = "childId"
        private const val EXTRA_CHILD_ADD_LIMIT_MODE = "childAddLimitMode"

        private const val STATE_PAGE = "page"

        private const val PAGE_LIST = 0
        private const val PAGE_TIME = 1
        private const val PAGE_DATE = 2

        fun newInstance(childId: String, categoryId: String, childAddLimitMode: Boolean) = BlockTemporarilyDialogFragment().apply {
            arguments = Bundle().apply {
                putString(EXTRA_CHILD_ID, childId)
                putString(EXTRA_CATEGORY_ID, categoryId)
                putBoolean(EXTRA_CHILD_ADD_LIMIT_MODE, childAddLimitMode)
            }
        }
    }

    val auth: ActivityViewModel by lazy { (activity as ActivityViewModelHolder).getActivityViewModel() }

    lateinit var binding: BlockTemporarilyDialogBinding

    override fun onCreateDialog(savedInstanceState: Bundle?) = object: BottomSheetDialog(context!!, theme) {
        override fun onBackPressed() {
            if (binding.flipper.displayedChild == PAGE_LIST) {
                super.onBackPressed()
            } else {
                binding.flipper.setInAnimation(context, R.anim.wizard_close_step_in)
                binding.flipper.setOutAnimation(context, R.anim.wizard_close_step_out)
                binding.flipper.displayedChild = PAGE_LIST
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = BlockTemporarilyDialogBinding.inflate(inflater, container, false)
        val database = RoomDatabase.with(context!!)
        val categoryId = arguments!!.getString(EXTRA_CATEGORY_ID)!!
        val childId = arguments!!.getString(EXTRA_CHILD_ID)!!
        val childAddLimitMode = arguments!!.getBoolean(EXTRA_CHILD_ADD_LIMIT_MODE)

        val childCategories = database.category().getCategoriesByChildId(childId)

        auth.authenticatedUserOrChild.observe(viewLifecycleOwner, Observer {
            val parentAuthenticated = it?.second?.type == UserType.Parent
            val childAuthenticated = it?.second?.id == childId && childAddLimitMode
            val anyoneAuthenticated = parentAuthenticated || childAuthenticated

            if (!anyoneAuthenticated) {
                dismissAllowingStateLoss()
            }
        })

        fun now() = RealTime.newInstance().apply {
            auth.logic.realTimeLogic.getRealTime(this)
        }.timeInMillis

        fun applyTimestamp(timestamp: Long) {
            val now = now()

            val category = childCategories.value?.find { it.id == categoryId } ?: run {
                Toast.makeText(context!!, R.string.error_general, Toast.LENGTH_SHORT).show()
                return
            }

            if (childAddLimitMode) {
                if (category.temporarilyBlocked) {
                    if (timestamp < category.temporarilyBlockedEndTime || category.temporarilyBlockedEndTime == 0L) {
                        Toast.makeText(context!!, R.string.manage_disable_time_limits_toast_time_not_increased_but_child_mode, Toast.LENGTH_LONG).show()
                        return
                    }
                }
            }

            if (timestamp > now) {
                auth.tryDispatchParentAction(
                        action = UpdateCategoryTemporarilyBlockedAction(
                                categoryId = categoryId,
                                blocked = true,
                                endTime = timestamp
                        ),
                        allowAsChild = childAddLimitMode
                )

                dismiss()
            } else {
                Toast.makeText(context!!, R.string.manage_disable_time_limits_toast_time_in_past, Toast.LENGTH_SHORT).show()
            }
        }

        if (savedInstanceState != null) {
            binding.flipper.displayedChild = savedInstanceState.getInt(STATE_PAGE)
        }

        val endOptionAdapter = SpecialModeOptionAdapter()

        childCategories.observe(viewLifecycleOwner, Observer { categories ->
            val now = now()

            val endTimes = categories
                    .filter { it.temporarilyBlocked && it.temporarilyBlockedEndTime != 0L }
                    .map { it.temporarilyBlockedEndTime }
                    .filter { it > now }
                    .distinct()
                    .sorted()

            endOptionAdapter.items =  endTimes.map {
                SpecialModeOption.Duration.FixedEndTime(timestamp = it)
            } + SpecialModeDuration.items
        })

        database.user().getChildUserByIdLive(childId).observe(viewLifecycleOwner, Observer { child ->
            if (child == null) {
                dismiss()
                return@Observer
            }

            endOptionAdapter.listener = object: SpecialModeOptionListener {
                override fun onItemClicked(item: SpecialModeOption) {
                    when (item) {
                        is SpecialModeOption.UntilTimeOption -> {
                            binding.flipper.setInAnimation(context!!, R.anim.wizard_open_step_in)
                            binding.flipper.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                            binding.flipper.displayedChild = PAGE_TIME
                        }
                        is SpecialModeOption.UntilDateOption -> {
                            binding.flipper.setInAnimation(context!!, R.anim.wizard_open_step_in)
                            binding.flipper.setOutAnimation(context!!, R.anim.wizard_open_step_out)
                            binding.flipper.displayedChild = PAGE_DATE
                        }
                        is SpecialModeOption.Duration -> {
                            applyTimestamp(item.getTime(
                                    currentTimestamp = now(),
                                    timezone = child.timeZone
                            )
                            )
                        }
                        SpecialModeOption.NoEndTimeOption -> throw IllegalArgumentException()
                    }.let {/* require handling all paths */}
                }
            }

            binding.calendarConfirmButton.setOnClickListener {
                applyTimestamp(
                        LocalDate.of(binding.calendar.year, binding.calendar.month + 1, binding.calendar.dayOfMonth)
                                .atStartOfDay(ZoneId.of(child.timeZone))
                                .toEpochSecond() * 1000
                )
            }

            binding.timeConfirmButton.setOnClickListener {
                applyTimestamp(
                        LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(now()),
                                ZoneId.of(child.timeZone)
                        )
                                .toLocalDate()
                                .atStartOfDay(ZoneId.of(child.timeZone))
                                .plusHours(binding.time.currentHour.toLong())
                                .plusMinutes(binding.time.currentMinute.toLong())
                                .toEpochSecond() * 1000
                )
            }
        })

        binding.endTimeOptionList.layoutManager = LinearLayoutManager(context)
        binding.endTimeOptionList.adapter = endOptionAdapter

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt(STATE_PAGE, binding.flipper.displayedChild)
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}