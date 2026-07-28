package com.kaislate.veldt.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The three "owners" a `ComposeView` insists on finding on its view tree, bundled
 * into one object.
 *
 * A window handed straight to the `WindowManager` has no Activity behind it, so
 * nothing supplies the lifecycle, the saved-state registry or the ViewModel store
 * that Compose looks up through the `ViewTree*Owner` extensions. Every overlay
 * window this app opens therefore gets one of these hung on its root view, and
 * [OverlayWindowManager] drives it by hand in the order an Activity would.
 *
 * Two ordering rules are load-bearing, and breaking either shows up as a crash the
 * instant the window is attached rather than as a visible glitch:
 *
 *  * the saved-state registry has to be restored while the lifecycle is still
 *    INITIALIZED, which is why that happens in `init` and not in [onStart];
 *  * the lifecycle has to be at CREATED or better by the time the view is attached,
 *    or Compose will not read the registry at all.
 *
 * Overlay windows are always rebuilt from scratch and never persist anything, so the
 * restore is fed a null bundle purely to honour the contract. Driving the registry
 * with lifecycle *events* rather than assigning states means the callers may skip
 * steps — [onDestroy] straight from RESUMED, for instance — and observers still see
 * the intermediate events in the right order.
 */
class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle
        get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedState.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    /** Call once the window itself has been accepted by the `WindowManager`. */
    fun onStart() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    /** Call after [onStart]. Work scoped to RESUMED only begins here. */
    fun onResume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun onStop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /**
     * Tears the owners down and releases anything the window's composition parked in
     * the ViewModel store.
     *
     * Deliberately tolerant: the teardown paths call this from whatever state they
     * happen to be in, and sometimes twice over, so it must neither throw nor
     * double-report. The store is emptied only after observers have been told the
     * lifecycle is gone.
     */
    fun onDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
