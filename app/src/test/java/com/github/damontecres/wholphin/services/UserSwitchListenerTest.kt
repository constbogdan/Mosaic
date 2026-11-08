package com.github.damontecres.wholphin.services

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import com.github.damontecres.wholphin.data.SeerrServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrServerUsers
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.test.user
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.configure
import com.github.damontecres.wholphin.util.reset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [30])
class UserSwitchListenerTest {
    private val dispatcher = StandardTestDispatcher()
    private val activity = mockk<AppCompatActivity>()
    private val lifecycle = LifecycleRegistry.createUnsafe(activity)
    private val users = MutableStateFlow<JellyfinUser?>(null)
    private val repository = mockk<ServerRepository>()
    private val seerr = mockk<SeerrServerRepository>(relaxed = true)
    private val dao = mockk<SeerrServerDao>()
    private val home = mockk<HomeSettingsService>(relaxed = true)
    private val api = mockk<SeerrApi>(relaxed = true)
    private val plugin = mockk<ServerPluginApi>()

    @Before
    fun setup() {
        WholphinDispatchers.configure(dispatcher)
        every { activity.lifecycle } returns lifecycle
        every { repository.currentUserFlow } returns users
        every { repository.serverPluginInstalled } returns MutableStateFlow(false)
        coEvery { plugin.checkInstalled() } returns false
        every { home.currentSettings } returns MutableStateFlow(HomePageResolvedSettings.EMPTY)
        coEvery { dao.getUsersByJellyfinUser(any()) } returns emptyList()
        lifecycle.currentState = Lifecycle.State.CREATED
        UserSwitchListener(activity, repository, seerr, dao, api, home, plugin)
    }

    @After
    fun cleanup() {
        lifecycle.currentState = Lifecycle.State.DESTROYED
        WholphinDispatchers.reset()
    }

    @Test
    fun `switching user cancels old Seerr authentication without reporting an old-session error`() =
        runTest(dispatcher) {
            var cancelled = false
            val first = user(UUID.randomUUID())
            val seerrUser = SeerrUser(1, 1, SeerrAuthMethod.API_KEY, null, null, "fixture")
            coEvery { dao.getUsersByJellyfinUser(1) } returns listOf(seerrUser)
            coEvery { dao.getServer(1) } returns SeerrServerUsers(SeerrServer(1, "http://seerr.test"), listOf(seerrUser))
            coEvery { api.api.usersApi.authMeGet() } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            users.value = first
            runCurrent()
            users.value = first.copy(id = UUID.randomUUID(), rowId = 2)
            runCurrent()
            assertTrue(cancelled)
            verify(exactly = 0) { seerr.error(any(), any(), any()) }
            coVerify(exactly = 0) { seerr.set(any(), any(), any()) }
        }

    @Test
    fun `same user UUID on another server refreshes Seerr and Home but timestamp changes do not`() =
        runTest(dispatcher) {
            runCurrent()
            val first = user(UUID.randomUUID())
            users.value = first
            runCurrent()
            users.value = first.copy(lastUsed = java.time.ZonedDateTime.now())
            runCurrent()
            users.value = first.copy(serverId = UUID.randomUUID(), rowId = 2)
            runCurrent()
            verify(exactly = 3) { seerr.clear() }
            coVerify(exactly = 1) { home.loadCurrentSettings(first) }
            coVerify(exactly = 1) { home.loadCurrentSettings(first.copy(serverId = users.value!!.serverId, rowId = 2)) }
            coVerify(exactly = 1) { dao.getUsersByJellyfinUser(1) }
            coVerify(exactly = 1) { dao.getUsersByJellyfinUser(2) }
        }
}
