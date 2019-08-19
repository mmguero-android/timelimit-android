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
import android.widget.CheckBox
import androidx.lifecycle.Observer
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.databinding.DiagnoseExperimentalFlagFragmentBinding
import io.timelimit.android.livedata.liveDataFromValue
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.main.ActivityViewModelHolder
import io.timelimit.android.ui.main.AuthenticationFab

class DiagnoseExperimentalFlagFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val activity: ActivityViewModelHolder = activity as ActivityViewModelHolder
        val database = DefaultAppLogic.with(context!!).database
        val auth = activity.getActivityViewModel()

        val binding = DiagnoseExperimentalFlagFragmentBinding.inflate(inflater, container, false)

        AuthenticationFab.manageAuthenticationFab(
                fab = binding.fab,
                shouldHighlight = auth.shouldHighlightAuthenticationButton,
                authenticatedUser = auth.authenticatedUser,
                doesSupportAuth = liveDataFromValue(true),
                fragment = this
        )

        binding.fab.setOnClickListener { activity.showAuthenticationScreen() }

        val flags = DiagnoseExperimentalFlagItem.items
        val checkboxes = flags.map {
            CheckBox(context).apply {
                setText(it.label)
            }
        }

        checkboxes.forEach { binding.container.addView(it) }

        database.config().experimentalFlags.observe(this, Observer { setFlags ->
            flags.forEachIndexed { index, flag ->
                val checkbox = checkboxes[index]
                val isFlagSet = (setFlags and flag.enableFlags) == flag.enableFlags

                checkbox.setOnCheckedChangeListener { _, _ -> }
                checkbox.isChecked = isFlagSet
                checkbox.isEnabled = flag.enable(setFlags)
                checkbox.setOnCheckedChangeListener { _, didCheck ->
                    if (didCheck != isFlagSet) {
                        if (auth.requestAuthenticationOrReturnTrue()) {
                            Threads.database.execute {
                                if (didCheck) {
                                    database.config().setExperimentalFlag(flag.enableFlags, true)
                                } else {
                                    database.config().setExperimentalFlag(flag.disableFlags, false)
                                }
                            }
                        } else {
                            checkbox.isChecked = isFlagSet
                        }
                    }
                }
            }
        })

        return binding.root
    }
}

data class DiagnoseExperimentalFlagItem(
        val label: Int,
        val enableFlags: Long,
        val disableFlags: Long,
        val enable: (flags: Long) -> Boolean
) {
    companion object {
        val items = listOf(
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_lom,
                        enableFlags = ExperimentalFlags.DISABLE_BLOCK_ON_MANIPULATION,
                        disableFlags = ExperimentalFlags.DISABLE_BLOCK_ON_MANIPULATION,
                        enable = { flags ->
                            (!BuildConfig.storeCompilant) and ((flags and ExperimentalFlags.MANIPULATION_ANNOY_USER_ONLY) == 0L)
                        }
                ),
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_slb,
                        enableFlags = ExperimentalFlags.SYSTEM_LEVEL_BLOCKING,
                        disableFlags = ExperimentalFlags.SYSTEM_LEVEL_BLOCKING,
                        enable = { !BuildConfig.storeCompilant }
                ),
                DiagnoseExperimentalFlagItem(
                        label = R.string.diagnose_exf_mau,
                        enableFlags = ExperimentalFlags.MANIPULATION_ANNOY_USER,
                        disableFlags = ExperimentalFlags.MANIPULATION_ANNOY_USER_ONLY,
                        enable = { !BuildConfig.storeCompilant }
                )
        )
    }
}
