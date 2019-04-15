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
package io.timelimit.android.ui.diagnose

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.DiagnoseForegroundAppFragmentBinding
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab
import io.timelimit.android.ui.main.getActivityViewModel
import io.timelimit.android.util.TimeTextUtil

class DiagnoseForegroundAppFragment : Fragment() {
    companion object {
        private val buttonIntervals = listOf(
                0,
                5 * 1000,
                30 * 1000,
                60 * 1000,
                15 * 60 * 1000,
                60 * 60 * 1000,
                24 * 60 * 60 * 1000,
                7 * 24 * 60 * 60 * 1000
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val activity: ActivityViewModelHolder = activity as ActivityViewModelHolder
        val binding = DiagnoseForegroundAppFragmentBinding.inflate(inflater, container, false)
        val auth = activity.getActivityViewModel()
        val logic = DefaultAppLogic.with(context!!)
        val currentValue = logic.database.config().getForegroundAppQueryIntervalAsync()
        val currentId = currentValue.map {
            val res = buttonIntervals.indexOf(it.toInt())

            if (res == -1)
                0
            else
                res
        }

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                doesSupportAuth = liveDataFromValue(true),
                fragment = this
        )

        binding.fab.setOnClickListener { activity.showAuthenticationScreen() }

        val allButtons = buttonIntervals.mapIndexed { index, interval ->
            RadioButton(context!!).apply {
                id = index

                if (interval == 0) {
                    setText(R.string.diagnose_fga_query_range_min)
                } else if (interval < 60 * 1000) {
                    text = TimeTextUtil.seconds(interval / 1000, context!!)
                } else {
                    text = TimeTextUtil.time(interval, context!!)
                }
            }
        }

        allButtons.forEach { binding.radioGroup.addView(it) }

        currentId.observe(this, Observer {
            binding.radioGroup.check(it)
        })

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val oldId = currentId.value

            if (oldId != null && checkedId != oldId) {
                if (auth.requestAuthenticationOrReturnTrue()) {
                    val newValue = buttonIntervals[checkedId]

                    Threads.database.execute {
                        logic.database.config().setForegroundAppQueryIntervalSync(newValue.toLong())
                    }
                } else {
                    binding.radioGroup.check(oldId)
                }
            }
        }

        return binding.root
    }
}
