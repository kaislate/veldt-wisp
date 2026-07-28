package com.kaislate.veldt.domain.overlay

import javax.inject.Inject

/**
 * Puts the pill on screen.
 *
 * A one-line wrapper on purpose. It exists so that the state machine deciding *when*
 * the pill should appear takes a dependency on the intent rather than on the overlay
 * repository itself, which keeps the "may I also hide it / read its state" surface out
 * of reach of code whose only job is to show it.
 *
 * Invoked as a function — `showIsland()` — so call sites read as the action they are.
 */
class ShowIslandUseCase @Inject constructor(
    private val overlay: OverlayRepository
) {
    operator fun invoke() {
        overlay.showPill()
    }
}
