package com.github.damontecres.wholphin.services

import androidx.datastore.core.DataStore
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.MostRecentServer
import com.github.damontecres.wholphin.data.model.JellyfinServerUsers
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.HdrOverrideMode
import com.github.damontecres.wholphin.preferences.update
import com.github.damontecres.wholphin.preferences.updatePlaybackOverrides
import com.github.damontecres.wholphin.test.currentUser
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.Version
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.UUID

class AppUpgradeHandlerTest {
    private class Store(
        initial: AppPreferences,
    ) : DataStore<AppPreferences> {
        override val data = MutableStateFlow(initial)

        override suspend fun updateData(transform: suspend (t: AppPreferences) -> AppPreferences): AppPreferences =
            transform(data.value).also { data.value = it }
    }

    private val current = currentUser(UUID.randomUUID(), UUID.randomUUID())
    private val serverDao = mockk<JellyfinServerDao>()
    private val recent = TestMostRecentServerProvider()

    private fun handler() = AppUpgradeHandler(mockk(), mockk(), mockk(), serverDao, mockk(), recent)

    private fun legacy() =
        AppPreferences.getDefaultInstance().update {
            currentServerId = current.server.id.toServerString()
            currentUserId = current.user.id.toServerString()
        }

    @Test
    fun `Mosaic versions beyond upstream gates migrate legacy session and HDR settings once`() =
        runTest {
            coEvery { serverDao.getServer(current.server.id) } returns JellyfinServerUsers(current.server, listOf(current.user))
            val store = Store(legacy().updatePlaybackOverrides { directPlayDolbyVisionEL = true })
            val handler = handler()
            handler.upgradeApp(Version.fromString("1.0.42"), Version.fromString("1.0.43"), store)
            assertEquals(MostRecentServer.from(current), recent.get())
            assertEquals("", store.data.value.currentServerId)
            assertEquals("", store.data.value.currentUserId)
            assertEquals(HdrOverrideMode.HDR_OVERRIDE_ENABLE, store.data.value.playbackPreferences.overrides.doviProfile7Override)
            assertFalse(store.data.value.playbackPreferences.overrides.directPlayDolbyVisionEL)
            val migrated = store.data.value
            handler.upgradeApp(Version.fromString("1.0.43"), Version.fromString("1.0.44"), store)
            assertEquals(migrated, store.data.value)
            assertEquals(MostRecentServer.from(current), recent.get())
        }

    @Test
    fun `legacy server without a user still restores the user picker`() =
        runTest {
            coEvery { serverDao.getServer(current.server.id) } returns JellyfinServerUsers(current.server, emptyList())
            val store = Store(legacy().update { currentUserId = "" })
            handler().upgradeApp(Version.fromString("1.0.42"), Version.fromString("1.0.43"), store)
            assertEquals(MostRecentServer.Server(current.server.id, current.server.url), recent.get())
        }

    @Test
    fun `new session and explicit HDR choice are not overwritten by legacy settings`() =
        runTest {
            val other: CurrentUser = currentUser(UUID.randomUUID(), UUID.randomUUID())
            recent.save(other)
            val store =
                Store(
                    legacy().updatePlaybackOverrides {
                        directPlayDolbyVisionEL = true
                        doviProfile7Override = HdrOverrideMode.HDR_OVERRIDE_DISABLE
                    },
                )
            handler().upgradeApp(Version.fromString("1.0.42"), Version.fromString("1.0.43"), store)
            assertEquals(MostRecentServer.from(other), recent.get())
            assertEquals(HdrOverrideMode.HDR_OVERRIDE_DISABLE, store.data.value.playbackPreferences.overrides.doviProfile7Override)
            assertFalse(store.data.value.playbackPreferences.overrides.directPlayDolbyVisionEL)
        }
}
