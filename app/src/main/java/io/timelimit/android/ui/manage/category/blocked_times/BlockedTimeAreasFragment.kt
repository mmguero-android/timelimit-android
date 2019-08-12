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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import io.timelimit.android.R
import io.timelimit.android.data.Database
import io.timelimit.android.data.customtypes.ImmutableBitmask
import io.timelimit.android.data.model.Category
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.sync.actions.UpdateCategoryBlockedTimesAction
import io.timelimit.android.ui.main.ActivityViewModel
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.ui.manage.category.ManageCategoryFragmentArgs
import io.timelimit.android.ui.manage.category.blocked_times.copy.CopyBlockedTimeAreasDialogFragment
import kotlinx.android.synthetic.main.fragment_blocked_time_areas.*
import java.util.*

class BlockedTimeAreasFragment : Fragment() {
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

    fun updateBlockedTimes(oldMask: BitSet, newMask: BitSet) {
        if (
                auth.tryDispatchParentAction(
                        UpdateCategoryBlockedTimesAction(
                                categoryId = params.categoryId,
                                blockedTimes = ImmutableBitmask(newMask)
                        )
                )
        ) {
            Snackbar.make(coordinator, R.string.blocked_time_areas_snackbar_modified, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.generic_undo) {
                        auth.tryDispatchParentAction(
                                UpdateCategoryBlockedTimesAction(
                                        categoryId = params.categoryId,
                                        blockedTimes = ImmutableBitmask(oldMask)
                                )
                        )
                    }
                    .show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layoutManager = GridLayoutManager(context, items.value!!.recommendColumns)
        layoutManager.spanSizeLookup = SpanSizeLookup(items.value!!)

        val adapter = Adapter(items.value!!)

        items.observe(this, Observer {
            layoutManager.spanCount = it.recommendColumns
            layoutManager.spanSizeLookup = SpanSizeLookup(it)
            adapter.items = it
        })

        recycler.adapter = adapter
        recycler.layoutManager = layoutManager

        category.observe(this, Observer { adapter.blockedTimeAreas = it?.blockedMinutesInWeek?.dataNotToModify })

        btn_help.setOnClickListener {
            BlockedTimeAreasHelpDialog().show(fragmentManager!!)
        }

        btn_copy_to_other_days.setOnClickListener {
            if (auth.requestAuthenticationOrReturnTrue()) {
                CopyBlockedTimeAreasDialogFragment.newInstance(params).apply {
                    setTargetFragment(this@BlockedTimeAreasFragment, 0)
                }.show(fragmentManager!!)
            }
        }

        adapter.handlers = object: Handlers {
            override fun onMinuteTileClick(time: MinuteTile) {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    val selectedMinuteOfWeek = adapter.selectedMinuteOfWeek
                    val blockedTimeAreas = adapter.blockedTimeAreas

                    if (blockedTimeAreas == null) {
                        // nothing to work with
                    } else if (selectedMinuteOfWeek == null) {
                        adapter.selectedMinuteOfWeek = time.minuteOfWeek
                    } else if (selectedMinuteOfWeek == time.minuteOfWeek) {
                        adapter.selectedMinuteOfWeek = null

                        val newBlockMask = blockedTimeAreas.clone() as BitSet
                        newBlockMask.set(
                                selectedMinuteOfWeek,
                                selectedMinuteOfWeek + items.value!!.minutesPerTile,
                                !newBlockMask[selectedMinuteOfWeek]
                        )

                        updateBlockedTimes(blockedTimeAreas, newBlockMask)
                    } else {
                        var times = selectedMinuteOfWeek to time.minuteOfWeek
                        adapter.selectedMinuteOfWeek = null

                        // sort selected times
                        if (times.first > times.second) {
                            times = times.second to times.first
                        }

                        // mark until the end
                        times = times.first to (times.second + items.value!!.minutesPerTile - 1)

                        // get majority of current value
                        var allowed = 0
                        var blocked = 0

                        for (i in times.first..times.second) {
                            if (blockedTimeAreas[i]) {
                                blocked++
                            } else {
                                allowed++
                            }
                        }

                        val isMajorityBlocked = blocked > allowed
                        val shouldBlock = !isMajorityBlocked

                        val newBlockMask = blockedTimeAreas.clone() as BitSet
                        newBlockMask.set(times.first, times.second + 1, shouldBlock)

                        updateBlockedTimes(blockedTimeAreas, newBlockMask)
                    }
                }
            }
        }

        run {
            val spinnerAdapter = ArrayAdapter.createFromResource(context!!, R.array.days_of_week_array, android.R.layout.simple_spinner_item)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner_day.adapter = spinnerAdapter
            spinner_day.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedDay = items.value!!.getDayOfPosition(
                            layoutManager.findFirstVisibleItemPosition()
                    )

                    if (selectedDay != position) {
                            layoutManager.scrollToPositionWithOffset(
                                    items.value!!.getPositionOfItem(
                                            DayHeader(position)
                                    ),
                                    0
                            )
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // ignore
                }
            }
        }

        recycler.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    items.value?.let { items ->
                        val selectedDay = items.getDayOfPosition(
                                layoutManager.findFirstVisibleItemPosition()
                        )

                        if (selectedDay != spinner_day.selectedItemPosition) {
                            spinner_day.setSelection(selectedDay, true)
                        }
                    }
                }
            }
        })

        // bind detailed mode
        items.value = when (detailed_mode.isChecked) {
            true -> MinuteOfWeekItems
            false -> FifteenMinutesOfWeekItems
        }

        detailed_mode.setOnCheckedChangeListener { _, isChecked ->
            val oldValue = items.value
            val newValue = when (isChecked) {
                true -> MinuteOfWeekItems
                false -> FifteenMinutesOfWeekItems
            }

            if (oldValue != newValue) {
                val currentlyVisiblePosition = layoutManager.findFirstVisibleItemPosition()

                if (currentlyVisiblePosition == RecyclerView.NO_POSITION) {
                    items.value = newValue
                } else {
                    val currentlyVisibleItem = oldValue!!.getItemAtPosition(currentlyVisiblePosition)
                    val newVisiblePosition = newValue.getPositionOfItem(currentlyVisibleItem)

                    items.value = newValue
                    layoutManager.scrollToPositionWithOffset(newVisiblePosition, 0)
                }
            }
        }
    }
}
