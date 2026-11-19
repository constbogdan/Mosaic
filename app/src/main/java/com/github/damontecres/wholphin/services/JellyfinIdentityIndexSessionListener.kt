package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/** Observes authenticated sessions without delaying startup or scheduling recurring scans. */
@ActivityScoped
class JellyfinIdentityIndexSessionListener
    @Inject
    constructor(
        @ActivityContext context: Context,
        serverRepository: ServerRepository,
        private val index: JellyfinIdentityIndex,
    ) {
        private val activity = context as AppCompatActivity
        private var resumeRefresh: Job? = null

        init {
            activity.lifecycleScope.launchIO {
                observeIdentitySessions(serverRepository.current, index)
            }
        }

        /** No timer or scan on each card: a genuine app resume performs one cheap age check. */
        fun refreshOnResume() {
            if (resumeRefresh?.isActive == true) return
            resumeRefresh =
                activity.lifecycleScope.launchIO {
                    try {
                        index.refreshIfStale()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A failed refresh leaves the previous complete snapshot intact.
                    }
                }
        }
    }

internal suspend fun observeIdentitySessions(
    sessions: Flow<CurrentUser?>,
    index: JellyfinIdentityIndex,
) {
    sessions.collectLatest { owner ->
        if (owner != null) {
            try {
                index.refreshIfStale()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The index logs a bounded failure summary; the existing snapshot remains usable.
            }
        }
    }
}
