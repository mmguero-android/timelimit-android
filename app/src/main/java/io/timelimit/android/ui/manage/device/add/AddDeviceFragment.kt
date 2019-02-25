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
package io.timelimit.android.ui.manage.device.add


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.timelimit.android.R
import io.timelimit.android.databinding.FragmentAddDeviceBinding
import io.timelimit.android.extensions.showSafe
import io.timelimit.android.ui.main.getActivityViewModel

class AddDeviceFragment : BottomSheetDialogFragment() {
    companion object {
        private const val DIALOG_TAG = "adf"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentAddDeviceBinding.inflate(inflater, container, false)
        val model = ViewModelProviders.of(this).get(AddDeviceModel::class.java)

        model.status.observe(this, Observer {
            when (it) {
                Initializing -> {
                    binding.token = null
                    binding.message = null
                }
                Failed -> {
                    binding.token = null
                    binding.message = getString(R.string.error_network)
                }
                is ShowingToken -> {
                    binding.token = it.token
                    binding.message = null
                }
                is DidAddDevice -> {
                    binding.token = null
                    binding.message = getString(R.string.add_device_success, it.deviceName)
                }
                TokenExpired -> {
                    dismissAllowingStateLoss()
                }
            }.let { /* require handling all paths */ }
        })

        model.init(getActivityViewModel(activity!!))

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) {
        showSafe(fragmentManager, DIALOG_TAG)
    }
}
