package com.kaislate.veldt.domain.overlay

import javax.inject.Inject

/**
 * Takes the pill off screen.
 *
 * The counterpart to [ShowIslandUseCase] and the same shape for the same reason: the
 * caller gets the one verb it needs and no way to reach the rest of the repository.
 *
 * Idempotence is the repository's business, not this class's — hiding an already
 * hidden pill is a no-op there, so callers are free to be blunt about it.
 */
class HideIslandUseCase @Inject constructor(
    private val overlay: OverlayRepository
) {
    operator fun invoke() {
        overlay.hidePill()
    }
}
