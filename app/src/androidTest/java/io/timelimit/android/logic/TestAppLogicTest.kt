package io.timelimit.android.logic

import androidx.test.InstrumentationRegistry
import io.timelimit.android.data.model.NetworkTime
import io.timelimit.android.integration.platform.ProtectionLevel
import io.timelimit.android.integration.platform.dummy.DummyApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TestAppLogicTest {
    companion object {
        private const val PAUSE_L = 300L
        private const val PARENT_PASSWORD = "secretsecret123"
    }

    @Test
    fun testDoesNotBlockAppWithoutSetup() {
        runBlocking (Dispatchers.Main) {
            val appLogic = TestAppLogic(
                    maximumProtectionLevel = ProtectionLevel.SimpleDeviceAdmin,
                    context = InstrumentationRegistry.getTargetContext()
            )

            try {
                appLogic.timeApi.emulateTimePassing(PAUSE_L)
                delay(PAUSE_L)

                assertEquals(null, appLogic.platformIntegration.getAndResetShowAppLockScreen())

                appLogic.platformIntegration.foregroundApp = DummyApps.taskmanagerLocalApp.packageName

                appLogic.timeApi.emulateTimePassing(PAUSE_L)
                delay(PAUSE_L)

                assertEquals(null, appLogic.platformIntegration.getAndResetShowAppLockScreen())
            } finally {
                appLogic.logic.shutdown()
            }
        }
    }

    @Test
    fun testDoesBlockAppAfterSetup() {
        runBlocking (Dispatchers.Main) {
            val appLogic = TestAppLogic(
                    maximumProtectionLevel = ProtectionLevel.SimpleDeviceAdmin,
                    context = InstrumentationRegistry.getTargetContext()
            )

            try {
                appLogic.logic.appSetupLogic.setupForLocalUse(PARENT_PASSWORD, NetworkTime.Disabled, InstrumentationRegistry.getTargetContext())

                appLogic.timeApi.emulateTimePassing(PAUSE_L)
                delay(PAUSE_L)

                assertEquals(null, appLogic.platformIntegration.getAndResetShowAppLockScreen())

                appLogic.platformIntegration.foregroundApp = DummyApps.taskmanagerLocalApp.packageName

                appLogic.timeApi.emulateTimePassing(PAUSE_L)
                delay(PAUSE_L)

                assertEquals(DummyApps.taskmanagerLocalApp.packageName, appLogic.platformIntegration.getAndResetShowAppLockScreen())
            } finally {
                appLogic.logic.shutdown()
            }
        }
    }
}
