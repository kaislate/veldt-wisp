package com.kaislate.veldt.data.overlay

import com.kaislate.veldt.domain.overlay.OverlayRepository
import com.kaislate.veldt.overlay.OverlayWindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The production [OverlayRepository]: the seam between everything that decides
 * whether the pill should be up and the one class that actually talks to the
 * window manager.
 *
 * It adds exactly two things to [OverlayWindowManager] — a narrower vocabulary
 * (show / hide / is it up), and an observable answer to the third. Deciding *when*
 * to show belongs to `IslandStateMachine` above it; laying out and attaching the
 * window belongs to [OverlayWindowManager] below. Nothing here does either.
 *
 * Constructed by hand in the DI module rather than carrying an `@Inject`
 * constructor: what the graph binds is the [OverlayRepository] interface, and the
 * provider function is where that binding is spelled out.
 */
class OverlayRepositoryImplementation(
    private val windows: OverlayWindowManager
) : OverlayRepository {

    /**
     * The last instruction given, not an inspection of the window itself.
     *
     * [OverlayWindowManager.showIsland] can decline — the overlay permission may
     * have been revoked a moment ago, or the OEM may reject the window — and this
     * still turns true. That is deliberate: callers use it to avoid re-issuing an
     * instruction they have already given, and it is the window manager's own
     * business to keep trying to honour it.
     */
    private val visible = MutableStateFlow(false)

    /** Read-only view: only the two methods below may move it. */
    override val isPillVisible: StateFlow<Boolean> = visible.asStateFlow()

    override fun showPill() {
        // Window first, flag second, in both methods — an observer woken by the
        // flag then finds the window already in the state the flag advertises.
        windows.showIsland()
        visible.value = true
    }

    override fun hidePill() {
        windows.hide()
        visible.value = false
    }
}
