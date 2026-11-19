package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.showToast
import kotlinx.coroutines.CancellationException
import timber.log.Timber

suspend fun getDiscoverRating(
    id: Int,
    func: suspend () -> DiscoverRating,
): DiscoverRating? =
    try {
        func.invoke()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: ClientException) {
        if (ex.statusCode == 404) {
            Timber.w("No rating for %s", id)
        } else {
            Timber.e(ex, "Error fetching rating for %s", id)
        }
        null
    } catch (ex: Exception) {
        Timber.e(ex, "Error fetching rating for %s", id)
        null
    }

/**
 * Go To uses the same current-user verification as Seerr card selection.
 * A Seerr-supplied Jellyfin ID is never a navigation target.
 */
fun goToButtonDiscover(
    item: DiscoverItem,
    context: Context,
    navigationManager: NavigationManager,
) {
    navigationManager.navigateToSeerrItem(item, onUnresolved = { showToast(context, "No accessible Jellyfin item found") })
}
