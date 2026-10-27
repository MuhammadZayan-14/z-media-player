package app.resonance.core.domain.usecase

import app.resonance.core.domain.repository.FavoritesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ToggleFavoriteUseCase @Inject constructor(
    private val favorites: FavoritesRepository
) {
    /** Returns the new favourite state. */
    suspend operator fun invoke(mediaId: Long): Boolean {
        val nowFavorite = mediaId !in favorites.favoriteIds().first()
        favorites.setFavorite(mediaId, nowFavorite)
        return nowFavorite
    }
}
