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
package io.timelimit.android.ui.setup

import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import io.timelimit.android.BuildConfig
import io.timelimit.android.R
import io.timelimit.android.coroutines.runAsync
import io.timelimit.android.data.model.NetworkTime
import io.timelimit.android.databinding.FragmentSetupLocalModeBinding
import io.timelimit.android.livedata.mergeLiveData
import io.timelimit.android.logic.DefaultAppLogic
import kotlinx.android.synthetic.main.fragment_setup_local_mode.*

class SetupLocalModeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentSetupLocalModeBinding.inflate(inflater, container, false)

        val model = ViewModelProviders.of(this).get(SetupLocalModeModel::class.java)
        val navigation = Navigation.findNavController(container!!)

        mergeLiveData(binding.setPasswordView.passwordOk, model.status).observe(this, Observer {
            binding.nextBtn.isEnabled = it!!.first == true && it.second == SetupLocalModeModel.Status.Idle
        })

        model.status.observe(this, Observer {
            val isIdle = it == SetupLocalModeModel.Status.Idle

            binding.setPasswordView.isEnabled = isIdle
        })

        model.status.observe(this, Observer {
            if (it == SetupLocalModeModel.Status.Done) {
                navigation.popBackStack(R.id.overviewFragment, false)
            }
        })

        binding.nextBtn.setOnClickListener {
            model.trySetupWithPassword(
                    set_password_view.password.value!!,
                    SetupNetworkTimeVerification.readSelection(binding.networkTimeVerification)
            )
        }

        return binding.root
    }
}

class SetupLocalModeModel(application: Application): AndroidViewModel(application) {
    companion object {
        private const val LOG_TAG = "SetupLocalModeModel"
    }

    enum class Status {
        Idle, Running, Done
    }

    val status = MutableLiveData<Status>()

    init {
        status.value = Status.Idle
    }

    fun trySetupWithPassword(parentPassword: String, networkTimeVerification: NetworkTime) {
        runAsync {
            if (status.value != Status.Idle) {
                throw IllegalStateException()
            }

            status.value = Status.Running

            try {
                DefaultAppLogic.with(getApplication()).appSetupLogic.setupForLocalUse(parentPassword, networkTimeVerification, getApplication())
                status.value = Status.Done
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "setup failed", ex)
                }

                Toast.makeText(getApplication(), R.string.error_general, Toast.LENGTH_SHORT).show()

                status.value = Status.Idle
            }
        }
    }
}
