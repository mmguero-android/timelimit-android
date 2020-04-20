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
package io.timelimit.android.ui.setup.customserver


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.databinding.SelectCustomServerDialogFragmentBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.livedata.waitForNonNullValue
import io.timelimit.android.logic.DefaultAppLogic

class SelectCustomServerDialogFragment : BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "SelectCustomServerDialogFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = SelectCustomServerDialogFragmentBinding.inflate(inflater, container, false)
        val logic = DefaultAppLogic.with(context!!)
        val model = ViewModelProviders.of(this).get(SelectCustomServerModel::class.java)

        if (savedInstanceState == null) {
            runAsync {
                binding.url.setText(logic.database.config().getCustomServerUrlAsync().waitForNonNullValue())
            }
        }

        binding.defaultServerButton.setOnClickListener {
            model.checkAndSave("")
        }

        binding.okButton.setOnClickListener {
            model.checkAndSave(binding.url.text.toString().trim())
        }

        model.status.observe(this, Observer {
            when (it!!) {
                SelectCustomServerStatus.Done -> dismiss()
                SelectCustomServerStatus.Idle -> {
                    binding.isWorking = false
                    binding.executePendingBindings()

                    binding.url.requestFocus()
                }
                SelectCustomServerStatus.Working -> binding.isWorking = true
            }
        })

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) = showSafe(fragmentManager, DIALOG_TAG)
}
