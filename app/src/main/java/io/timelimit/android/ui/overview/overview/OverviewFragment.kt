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
package io.timelimit.android.ui.overview.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.coroutines.CoroutineFragment
import io.timelimit.android.data.model.*
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.logic.AppLogic
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.ReviewChildTaskAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.payment.RequiresPurchaseDialogFragment
import kotlinx.android.synthetic.main.fragment_overview.*
import kotlinx.coroutines.launch

class OverviewFragment : CoroutineFragment() {
    private val handlers: OverviewFragmentParentHandlers by lazy { parentFragment as OverviewFragmentParentHandlers }
    private val logic: AppLogic by lazy { DefaultAppLogic.with(requireContext()) }
    private val auth: ActivityViewModel by lazy { getActivityViewModel(requireActivity()) }
    private val model: OverviewFragmentModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = OverviewFragmentAdapter()

        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(requireContext())

        adapter.handlers = object: OverviewFragmentHandlers {
            override fun onAddUserClicked() {
                handlers.openAddUserScreen()
            }

            override fun onDeviceClicked(device: Device) {
                handlers.openManageDeviceScreen(deviceId = device.id)
            }

            override fun onUserClicked(user: User) {
                if (
                        user.restrictViewingToParents &&
                        logic.deviceUserId.value != user.id &&
                        !auth.requestAuthenticationOrReturnTrue()
                ) {
                    // do "nothing"/ request authentication
                } else {
                    when (user.type) {
                        UserType.Child -> handlers.openManageChildScreen(childId = user.id)
                        UserType.Parent -> handlers.openManageParentScreen(parentId = user.id)
                    }.let { }
                }
            }

            override fun onAddDeviceClicked() {
                launch {
                    if (logic.database.config().getDeviceAuthTokenAsync().waitForNonNullValue().isEmpty()) {
                        CanNotAddDevicesInLocalModeDialogFragment()
                                .apply { setTargetFragment(this@OverviewFragment, 0) }
                                .show(fragmentManager!!)
                    } else if (auth.requestAuthenticationOrReturnTrue()) {
                        handlers.openAddDeviceScreen()
                    }
                }
            }

            override fun onFinishSetupClicked() {
                handlers.openSetupDeviceScreen()
            }

            override fun onShowAllUsersClicked() {
                model.showAllUsers()
            }

            override fun onSetDeviceListVisibility(level: DeviceListItemVisibility) {
                model.showMoreDevices(level)
            }

            override fun onTaskConfirmed(task: ChildTask, hasPremium: Boolean) {
                if (hasPremium) {
                    auth.tryDispatchParentAction(
                            ReviewChildTaskAction(
                                    taskId = task.taskId,
                                    ok = true,
                                    time = logic.timeApi.getCurrentTimeInMillis()
                            )
                    )
                } else RequiresPurchaseDialogFragment().show(parentFragmentManager)
            }

            override fun onTaskRejected(task: ChildTask) {
                auth.tryDispatchParentAction(
                        ReviewChildTaskAction(
                                taskId = task.taskId,
                                ok = false,
                                time = logic.timeApi.getCurrentTimeInMillis()
                        )
                )
            }

            override fun onSkipTaskReviewClicked(task: ChildTask) {
                if (auth.requestAuthenticationOrReturnTrue()) model.hideTask(task.taskId)
            }
        }

        model.listEntries.observe(viewLifecycleOwner) { adapter.data = it }

        ItemTouchHelper(
                object: ItemTouchHelper.Callback() {
                    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                        val index = viewHolder.adapterPosition
                        val item = if (index == RecyclerView.NO_POSITION) null else adapter.data!![index]

                        if (item == OverviewFragmentHeaderIntro) {
                            return makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.START or ItemTouchHelper.END) or
                                    makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.END or ItemTouchHelper.END)
                        } else {
                            return 0
                        }
                    }

                    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = throw IllegalStateException()

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        // remove the introduction header
                        Threads.database.execute {
                            logic.database.config().setHintsShownSync(HintsToShow.OVERVIEW_INTRODUCTION)
                        }
                    }
                }
        ).attachToRecyclerView(recycler)
    }
}

interface OverviewFragmentParentHandlers {
    fun openAddUserScreen()
    fun openAddDeviceScreen()
    fun openManageDeviceScreen(deviceId: String)
    fun openManageChildScreen(childId: String)
    fun openManageParentScreen(parentId: String)
    fun openSetupDeviceScreen()
}
