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

package io.timelimit.android.ui.update

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import io.timelimit.android.BuildConfig
import io.timelimit.android.databinding.UpdateViewBinding
import io.timelimit.android.update.UpdateIntegration
import io.timelimit.android.livedata.map
import io.timelimit.android.livedata.switchMap
import io.timelimit.android.logic.AppLogic

object UpdateView {
    fun bind(
            view: UpdateViewBinding,
            appLogic: AppLogic,
            lifecycleOwner: LifecycleOwner,
            fragmentManager: FragmentManager
    ) {
        val context = view.root.context
        val config = appLogic.database.config()
        val supported = UpdateIntegration.doesSupportUpdates(context)
        val enabledLive = config.areUpdatesEnabledLive()
        val statusLive = config.getUpdateStatusLive()

        if (supported) {
            enabledLive.switchMap { enabled ->
                statusLive.map { status -> enabled to status }
            }.observe(lifecycleOwner, Observer { (enabled, status) ->
                if (enabled) {
                    if (status != null && status.versionCode > BuildConfig.VERSION_CODE) {
                        view.status = Status.UpdateAvailable
                        view.newVersion = status.versionName
                        view.changelog = status.getChangelog(view.root.context)
                    } else {
                        view.status = Status.NothingAvailable
                    }
                } else {
                    view.status = Status.Disabled
                }
            })
        } else {
            view.status = Status.Unsupported
        }

        view.handlers = object: Handlers {
            override fun enable() {
                EnableUpdatesDialogFragment().show(fragmentManager)
            }

            override fun check() {
                CheckUpdateDialogFragment().show(fragmentManager)
            }

            override fun download() {
                if (UpdateIntegration.hasRequiredPermission(context)) {
                    DownloadUpdateDialogFragment().show(fragmentManager)
                } else {
                    UpdateIntegration.requestPermission(context)
                }
            }
        }
    }

    enum class Status {
        Unsupported,
        Disabled,
        NothingAvailable,
        UpdateAvailable
    }

    interface Handlers {
        fun enable()
        fun check()
        fun download()
    }
}