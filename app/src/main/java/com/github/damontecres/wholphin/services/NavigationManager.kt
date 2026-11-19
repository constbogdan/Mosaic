package com.github.damontecres.wholphin.services

import androidx.navigation3.runtime.NavBackStack
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.acra.ACRA
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages navigating between pages and manages the app's back stack
 */
@Singleton
class NavigationManager
    @Inject
    constructor(
        private val ownedResolver: SeerrOwnedNavigationResolver,
        private val servers: ServerRepository,
    ) {
        var backStack: MutableList<Destination> = NavBackStack(Destination.Home())
        private val selectionScope = CoroutineScope(SupervisorJob() + WholphinDispatchers.Main.immediate)
        private var pendingSelection: Pair<Int, String>? = null
        private var selectionJob: Job? = null
        private var selectionGeneration = 0L
        private var lastCompletedSelection: Pair<Int, String>? = null
        private var lastCompletedDestination: Destination? = null

        /** Non-blocking, session-safe selection for Seerr-backed Movie and Series cards. */
        fun navigateToSeerrItem(
            item: DiscoverItem,
            onUnresolved: (suspend () -> Unit)? = null,
        ) {
            val origin = backStack.lastOrNull() ?: return
            val key = item.id to item.type.name
            if (lastCompletedSelection == key && origin === lastCompletedDestination) return
            val owner = servers.current.value
            if (owner == null) {
                if (onUnresolved == null) {
                    val destination = Destination.DiscoveredItem(item)
                    navigateTo(destination)
                    lastCompletedSelection = key
                    lastCompletedDestination = destination
                } else {
                    selectionScope.launch { onUnresolved() }
                }
                return
            }
            if (pendingSelection == key && selectionJob?.isActive == true) return
            selectionJob?.cancel()
            pendingSelection = key
            val generation = ++selectionGeneration
            selectionJob =
                selectionScope.launch {
                    try {
                        val destination = withContext(WholphinDispatchers.IO) { ownedResolver.resolve(item, owner) }
                        if (destination != null && servers.current.value === owner && backStack.lastOrNull() === origin) {
                            if (onUnresolved != null && destination is Destination.DiscoveredItem) {
                                onUnresolved()
                            } else {
                                navigateTo(destination)
                                lastCompletedSelection = key
                                lastCompletedDestination = destination
                            }
                        }
                    } finally {
                        if (generation == selectionGeneration) pendingSelection = null
                    }
                }
        }

        /**
         * Go to the specified [com.github.damontecres.wholphin.ui.nav.Destination]
         */
        fun navigateTo(destination: Destination) {
            backStack.add(destination)
            log()
        }

        /**
         * Go to the specified [Destination], but reset the back stack to Home first
         */
        fun navigateToFromDrawer(destination: Destination) {
            goToHome()
            backStack.add(destination)
            log()
        }

        /**
         * Go to the previous page
         */
        fun goBack() {
            synchronized(this) {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            }
            log()
        }

        /**
         * Go all the way back to the home page
         */
        fun goToHome() {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack[0] !is Destination.Home) {
                backStack[0] = Destination.Home()
            }
            log()
        }

        /**
         * Go all the way back to the home page, and reload it from scratch
         */
        fun reloadHome() {
            goToHome()
            val id = (backStack[0] as Destination.Home).id + 1
            backStack[0] = Destination.Home(id)
            log()
        }

        /**
         * Resets the backstack to the specified destination
         */
        fun replace(destination: Destination) {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack.isEmpty()) {
                backStack.add(0, destination)
            } else {
                backStack[0] = destination
            }
            log()
        }

        private fun log() {
            val dest = backStack.lastOrNull().toString()
            Timber.i("Current Destination: %s", dest)
            ACRA.errorReporter.putCustomData("destination", dest)
        }
    }
