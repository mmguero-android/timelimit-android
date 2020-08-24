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
package io.timelimit.android.integration.platform.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.lifecycle.Observer
import io.timelimit.android.data.model.ExperimentalFlags
import io.timelimit.android.logic.DefaultAppLogic

class AccessibilityService: AccessibilityService() {
    companion object {
        var instance: io.timelimit.android.integration.platform.android.AccessibilityService? = null
    }

    private var shutdown: Runnable? = null

    private var wasSplitScreen = false
    private var blockSplitScreen = false

    override fun onServiceConnected() {
        super.onServiceConnected()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.serviceInfo = AccessibilityServiceInfo().apply {
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                eventTypes = AccessibilityEvent.TYPE_WINDOWS_CHANGED
                flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        }

        instance = this

        val logic = DefaultAppLogic.with(this)

        val observer = Observer<Boolean> { blockSplitScreen = it }
        val blockSplitScreenLive = logic.database.config().isExperimentalFlagsSetAsync(ExperimentalFlags.BLOCK_SPLIT_SCREEN)

        blockSplitScreenLive.observeForever(observer)

        shutdown = Runnable { blockSplitScreenLive.removeObserver(observer) }
    }

    override fun onDestroy() {
        super.onDestroy()

        shutdown?.run(); shutdown = null

        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val isSplitScreen = windows.find { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER } != null

            if (isSplitScreen && !wasSplitScreen && blockSplitScreen) {
                performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            }

            wasSplitScreen = isSplitScreen
        }
    }

    override fun onInterrupt() {
        // ignore
    }

    fun showHomescreen() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }
}