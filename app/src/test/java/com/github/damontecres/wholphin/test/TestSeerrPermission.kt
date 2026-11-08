package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.api.seerr.model.PublicSettings
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.SeerrServer
import com.github.damontecres.wholphin.data.model.SeerrUser
import com.github.damontecres.wholphin.data.model.canRequestMovies
import com.github.damontecres.wholphin.data.model.canRequestTv
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.services.CurrentSeerr
import com.github.damontecres.wholphin.services.SeerrUserConfig
import org.junit.Assert
import org.junit.Test

class TestSeerrPermission {
    @Test
    fun `standard and 4K permissions remain independent and honor server capabilities`() {
        for (standard in listOf(false, true)) {
            for (fourK in listOf(false, true)) {
                for (server4k in listOf(false, true)) {
                    val user = SeerrUserConfig(id = 7, permissions = (if (standard) 32 else 0) or (if (fourK) 1024 else 0))
                    val current =
                        CurrentSeerr(
                            SeerrServer(1, "http://seerr.invalid"),
                            SeerrUser(1, 1, SeerrAuthMethod.API_KEY, null, null, "fixture"),
                            user,
                            PublicSettings(movie4kEnabled = server4k, series4kEnabled = server4k),
                        )
                    Assert.assertEquals(standard, current.requestMovieEnabled)
                    Assert.assertEquals(standard, current.requestTvEnabled)
                    Assert.assertEquals(fourK && server4k, current.request4kMovieEnabled)
                    Assert.assertEquals(fourK && server4k, current.request4kTvEnabled)
                    Assert.assertEquals(standard || fourK, user.canRequestMovies)
                    Assert.assertEquals(standard || fourK, user.canRequestTv)
                }
            }
        }
    }

    @Test
    fun `Is Admin`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 2,
            )
        Assert.assertTrue(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K))
    }

    @Test
    fun `Request any 4k has request movie 4k`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 1024,
            )
        Assert.assertFalse(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K))
    }

    @Test
    fun `Request movie 4k and does not have request tv 4k`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 2048,
            )
        Assert.assertFalse(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertFalse(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertFalse(user.hasPermission(SeerrPermission.REQUEST_4K))
    }

    @Test
    fun `Has explicit request 4k and children`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 7168,
            )
        Assert.assertFalse(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K))
    }

    @Test
    fun `Has explicit request 4k children`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 6144,
            )
        Assert.assertFalse(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertFalse(user.hasPermission(SeerrPermission.REQUEST_4K))
    }
}
