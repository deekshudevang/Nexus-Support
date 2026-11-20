package com.meshlink.app.notification

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class AssignmentNotificationHelperTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        AssignmentNotificationHelper.sdkIntOverride = null
    }

    @After
    fun teardown() {
        AssignmentNotificationHelper.sdkIntOverride = null
    }

    @Test
    fun `channel ID constant matches production expected value`() {
        assertEquals("meshlink_emergency_assignments", AssignmentNotificationHelper.CHANNEL_ID)
    }

    @Test
    fun `createNotificationChannel creates channel on Android O and above`() {
        AssignmentNotificationHelper.createNotificationChannel(context)

        val channel = notificationManager.getNotificationChannel(AssignmentNotificationHelper.CHANNEL_ID)
        assertNotNull("Emergency channel must be created", channel)
        assertEquals("Tactical Emergency Assignments", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun `permissionGranted_onTiramisu_postsNotification`() {
        AssignmentNotificationHelper.sdkIntOverride = Build.VERSION_CODES.TIRAMISU

        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        notificationManager.cancelAll()

        AssignmentNotificationHelper.showAssignmentHeadsUpNotification(
            context = context,
            assignmentId = "asgn-test-101",
            title = "Missing Hiker Sector 4",
            notes = "URGENT response needed"
        )

        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        assertEquals(1, shadowNotificationManager.allNotifications.size)
        val posted = shadowNotificationManager.allNotifications.first()
        assertNotNull(posted)
    }

    @Test
    fun `permissionDenied_onTiramisu_skipsNotificationWithoutCrashing`() {
        AssignmentNotificationHelper.sdkIntOverride = Build.VERSION_CODES.TIRAMISU

        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        notificationManager.cancelAll()

        AssignmentNotificationHelper.showAssignmentHeadsUpNotification(
            context = context,
            assignmentId = "asgn-test-102",
            title = "Flash Flood Alert",
            notes = "Evacuate valley floor"
        )

        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        assertEquals(0, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun `preTiramisu_postsNotificationWithoutRequiringPostNotifications`() {
        AssignmentNotificationHelper.sdkIntOverride = Build.VERSION_CODES.S // Android 12 (API 31)

        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        notificationManager.cancelAll()

        AssignmentNotificationHelper.showAssignmentHeadsUpNotification(
            context = context,
            assignmentId = "asgn-test-103",
            title = "Avalanche Warning",
            notes = "Deploy beacon team"
        )

        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        assertEquals(1, shadowNotificationManager.allNotifications.size)
    }

    @Test
    fun `exceptionDuringNotification_gracefullyDegradesWithoutCrashing`() {
        // Pass a mocked context that throws on getSystemService
        val brokenContext = io.mockk.mockk<Context>(relaxed = true)
        io.mockk.every { brokenContext.getSystemService(any()) } throws SecurityException("Device security restriction")

        // Must degrade cleanly without rethrowing
        AssignmentNotificationHelper.showAssignmentHeadsUpNotification(
            context = brokenContext,
            assignmentId = "asgn-test-104",
            title = "Crash Test",
            notes = "Should handle exception cleanly"
        )
    }
}
