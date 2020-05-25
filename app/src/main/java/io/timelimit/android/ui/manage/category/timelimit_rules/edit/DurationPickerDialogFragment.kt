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
package io.timelimit.android.ui.manage.category.timelimit_rules.edit

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import io.timelimit.android.R
import io.timelimit.android.async.Threads
import io.timelimit.android.databinding.DurationPickerDialogFragmentBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.view.SelectTimeSpanViewListener

class DurationPickerDialogFragment: DialogFragment() {
    companion object {
        private const val DIALOG_TAG = "DurationPickerDialogFragment"
        private const val TITLE_RES = "titleRes"
        private const val INDEX = "index"
        private const val START_TIME_IN_MILLIS = "startTimeInMillis"

        fun newInstance(titleRes: Int, index: Int, target: Fragment, startTimeInMillis: Int) = DurationPickerDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(TITLE_RES, titleRes)
                putInt(INDEX, index)
                putInt(START_TIME_IN_MILLIS, startTimeInMillis)
            }

            setTargetFragment(target, 0)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DurationPickerDialogFragmentBinding.inflate(LayoutInflater.from(context!!))
        val view = binding.duration
        val target = targetFragment as DurationPickerDialogFragmentListener
        val index = arguments!!.getInt(INDEX)
        val titleRes = arguments!!.getInt(TITLE_RES)
        val startTimeInMillis = arguments!!.getInt(START_TIME_IN_MILLIS)
        val config = DefaultAppLogic.with(context!!).database.config()

        if (savedInstanceState == null) {
            view.timeInMillis = startTimeInMillis.toLong()
        }

        config.getEnableAlternativeDurationSelectionAsync().observe(this, Observer {
            view.enablePickerMode(it)
        })

        view.listener = object: SelectTimeSpanViewListener {
            override fun onTimeSpanChanged(newTimeInMillis: Long) = Unit

            override fun setEnablePickerMode(enable: Boolean) {
                Threads.database.execute {
                    config.setEnableAlternativeDurationSelectionSync(enable)
                }
            }
        }

        return AlertDialog.Builder(context!!, theme)
                .setTitle(titleRes)
                .setView(binding.root)
                .setPositiveButton(R.string.generic_ok) { _, _ ->
                    target.onDurationSelected(view.timeInMillis.toInt(), index)
                }
                .setNegativeButton(R.string.generic_cancel, null)
                .create()
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}

interface DurationPickerDialogFragmentListener {
    fun onDurationSelected(durationInMillis: Int, index: Int)
}