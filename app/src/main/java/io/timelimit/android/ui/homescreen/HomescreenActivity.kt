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
package io.timelimit.android.ui.homescreen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.timelimit.android.R
import io.timelimit.android.databinding.ActivityHomescreenItemBinding
import kotlinx.android.synthetic.main.activity_homescreen.*

class HomescreenActivity: AppCompatActivity() {
    companion object {
        private const val FORCE_SELECTION = "forceSelection"
    }

    private val model: HomescreenModel by lazy { ViewModelProvider(this).get(HomescreenModel::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_homescreen)

        model.handleLaunchIfNotYetExecuted(intent.getBooleanExtra(FORCE_SELECTION, false))

        model.status.observe(this, Observer { status ->
            when (status) {
                SelectionListHomescreenStatus -> {
                    hideProgress()

                    initOptionList()
                }
                is TryLaunchHomescreenStatus -> {
                    hideOptionList()
                    hideProgress()

                    if (!status.didTry) {
                        status.didTry = true

                        try {
                            startActivity(HomescreenUtil.openHomescreenIntent().setComponent(status.component))
                        } catch (ex: ActivityNotFoundException) {
                            model.showSelectionList()
                        }
                    }
                }
                is DelayHomescreenStatus -> {
                    hideOptionList()

                    showProgress(status.progress)
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        model.handleLaunch(intent?.getBooleanExtra(FORCE_SELECTION, false) ?: false)
    }

    private fun initOptionList() {
        maincard.visibility = View.VISIBLE

        val options = HomescreenUtil.launcherOptions(this)

        launcher_options.removeAllViews()

        options.forEach { option ->
            val view = ActivityHomescreenItemBinding.inflate(LayoutInflater.from(this), launcher_options, true)

            view.label = try {
                packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(option.packageName, 0)
                ).toString()
            } catch (ex: PackageManager.NameNotFoundException) {
                null
            } ?: option.packageName

            try {
                view.icon.setImageDrawable(packageManager.getApplicationIcon(option.packageName))
            } catch (ex: Exception) {
                view.icon.setImageResource(R.mipmap.ic_launcher_round)
            }

            view.root.setOnClickListener {
                try {
                    startActivity(
                            HomescreenUtil.openHomescreenIntent().setComponent(option)
                    )

                    hideOptionList()
                    model.saveDefaultOption(option)
                } catch (ex: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.error_general, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun hideOptionList() {
        maincard.visibility = View.GONE

        launcher_options.removeAllViews()
    }

    private fun showProgress(progresss: Int) {
        progress_card.visibility = View.VISIBLE
        progress_bar.progress = progresss
    }

    private fun hideProgress() {
        progress_card.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()

        model.handleResume()
    }

    override fun onPause() {
        super.onPause()

        model.handlePause()
    }
}