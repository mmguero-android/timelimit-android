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
package io.timelimit.android.ui.manipulation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import io.timelimit.android.R
import io.timelimit.android.livedata.map
import io.timelimit.android.logic.DefaultAppLogic
import io.timelimit.android.ui.manage.device.manage.ManipulationWarningTypeLabel
import io.timelimit.android.ui.manage.device.manage.ManipulationWarnings
import io.timelimit.android.util.TimeTextUtil
import kotlinx.android.synthetic.main.annoy_activity.*

class AnnoyActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_DURATION = "duration"

        fun start(context: Context, duration: Long) {
            context.startActivity(
                    Intent(context, AnnoyActivity::class.java)
                            .putExtra(EXTRA_DURATION, duration)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val duration = intent!!.getLongExtra(EXTRA_DURATION, 10)
        val model = ViewModelProviders.of(this).get(AnnoyModel::class.java)
        val logic = DefaultAppLogic.with(this)

        setContentView(R.layout.annoy_activity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (logic.platformIntegration.setLockTaskPackages(listOf(packageName))) {
                startLockTask()
            }
        }

        model.init(duration = duration)
        model.countdown.observe(this, Observer {
            if (it == 0L) {
                shutdown()
            }

            annoy_timer.setText(
                    getString(R.string.annoy_timer, TimeTextUtil.seconds(it.toInt(), this@AnnoyActivity))
            )
        })

        logic.deviceEntry.map {
            val reasonItems = (it?.let { ManipulationWarnings.getFromDevice(it) } ?: ManipulationWarnings.empty)
                    .current
                    .map {
                        getString(ManipulationWarningTypeLabel.getLabel(it))
                    }

            if (reasonItems.isEmpty()) {
                null
            } else {
                getString(R.string.annoy_reason, reasonItems.joinToString(separator = ", "))
            }
        }.observe(this, Observer {
            if (it.isNullOrEmpty()) {
                annoy_reason.visibility = View.GONE
            } else {
                annoy_reason.visibility = View.VISIBLE
                annoy_reason.setText(it)
            }
        })
    }

    private fun shutdown() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopLockTask()
            finish()
        }
    }

    override fun onBackPressed() {
        // super.onBackPressed()
        // just ignore it
    }
}
