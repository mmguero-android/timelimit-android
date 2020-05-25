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
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import io.timelimit.android.extensions.showSafe

class TimePickerDialogFragment: DialogFragment() {
    companion object {
        private const val INDEX = "index"
        private const val START_MINUTE_OF_DAY = "startMinuteOfDay"
        private const val DIALOG_TAG = "TimePickerDialogFragment"

        fun newInstance(
                editTimeLimitRuleDialogFragment: EditTimeLimitRuleDialogFragment,
                index: Int,
                startMinuteOfDay: Int
        ) = TimePickerDialogFragment().apply {
            setTargetFragment(editTimeLimitRuleDialogFragment, 0)
            arguments = Bundle().apply {
                putInt(INDEX, index)
                putInt(START_MINUTE_OF_DAY, startMinuteOfDay)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val fragment = targetFragment as EditTimeLimitRuleDialogFragment
        val index = arguments!!.getInt(INDEX)
        val startMinuteOfDay = arguments!!.getInt(START_MINUTE_OF_DAY)

        return TimePickerDialog(context, theme, { _, hour, minute ->
            fragment.handleTimePickerResult(index, hour * 60 + minute)
        }, startMinuteOfDay / 60, startMinuteOfDay % 60, true)
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}