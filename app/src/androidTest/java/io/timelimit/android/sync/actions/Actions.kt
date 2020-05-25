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
package io.timelimit.android.sync.actions

import io.timelimit.android.data.model.AppRecommendation
import io.timelimit.android.integration.platform.NewPermissionStatus
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.RuntimePermissionStatus
import io.timelimit.android.sync.network.ParentPassword
import org.json.JSONObject
import org.junit.Test

class Actions {
    private val appLogicActions: List<AppLogicAction> = listOf(
            AddUsedTimeActionVersion2(
                    trustedTimestamp = 13,
                    dayOfEpoch = 674,
                    items = listOf(
                            AddUsedTimeActionItem(
                                    categoryId = "abcdef",
                                    sessionDurationLimits = setOf(
                                            AddUsedTimeActionItemSessionDurationLimitSlot(
                                                    startMinuteOfDay = 10,
                                                    endMinuteOfDay = 23,
                                                    sessionPauseDuration = 1000,
                                                    maxSessionDuration = 2000
                                            ),
                                            AddUsedTimeActionItemSessionDurationLimitSlot(
                                                    startMinuteOfDay = 14,
                                                    endMinuteOfDay = 23,
                                                    sessionPauseDuration = 1000,
                                                    maxSessionDuration = 2000
                                            )
                                    ),
                                    additionalCountingSlots = setOf(
                                            AddUsedTimeActionItemAdditionalCountingSlot(21, 31),
                                            AddUsedTimeActionItemAdditionalCountingSlot(30, 55)
                                    ),
                                    extraTimeToSubtract = 100,
                                    timeToAdd = 1255
                            )
                    )
            ),
            AddInstalledAppsAction(
                    apps = listOf(
                            InstalledApp(
                                    packageName = "com.demo.app",
                                    isLaunchable = true,
                                    title = "Demo",
                                    recommendation = AppRecommendation.Blacklist
                            )
                    )
            ),
            RemoveInstalledAppsAction(
                    packageNames = listOf("com.something.test")
            ),
            UpdateAppActivitiesAction(
                    removedActivities = listOf("com.demo" to "com.demo.MainActivity", "com.demo" to "com.demo.DemoActivity"),
                    updatedOrAddedActivities = listOf(
                            AppActivityItem(
                                    packageName = "com.demo.two",
                                    title = "Test",
                                    className = "com.demo.TwoActivity"
                            )
                    )
            ),
            SignOutAtDeviceAction,
            UpdateDeviceStatusAction(
                    newProtectionLevel = ProtectionLevel.PasswordDeviceAdmin,
                    didReboot = true,
                    isQOrLaterNow = true,
                    newAccessibilityServiceEnabled = true,
                    newAppVersion = 10,
                    newNotificationAccessPermission = NewPermissionStatus.Granted,
                    newOverlayPermission = RuntimePermissionStatus.NotRequired,
                    newUsageStatsPermissionStatus = RuntimePermissionStatus.NotGranted
            ),
            TriedDisablingDeviceAdminAction
    )

    private val parentActions: List<ParentAction> = listOf(
            AddCategoryAppsAction(
                    categoryId = "abedge",
                    packageNames = listOf("com.demo.one", "com.demo.two")
            )
            // this list does not contain all actions
    )

    private val childActions: List<ChildAction> = listOf(
            ChildSignInAction,
            ChildChangePasswordAction(
                    password = ParentPassword.createSync("test")
            )
    )

    @Test
    fun testActionSerializationAndDeserializationWorks() {
        appLogicActions.forEach { originalAction ->
            val serializedAction = SerializationUtil.serializeAction(originalAction)
            val parsedAction = ActionParser.parseAppLogicAction(JSONObject(serializedAction))

            assert(parsedAction == originalAction)
        }
    }

    @Test
    fun testCanSerializeParentActions() {
        parentActions.forEach {
            SerializationUtil.serializeAction(it)
        }
    }

    @Test
    fun testCanSerializeChildActions() {
        childActions.forEach {
            SerializationUtil.serializeAction(it)
        }
    }
}