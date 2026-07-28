package com.kaislate.veldt.overlay

import android.media.session.PlaybackState
import com.kaislate.veldt.domain.overlay.HideIslandUseCase
import com.kaislate.veldt.domain.overlay.OverlayRepository
import com.kaislate.veldt.domain.overlay.ShowIslandUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Behavioural tests for the plumbing: idempotence, timer arming/disarming,
 * environment merging, and the shutdown contract. The decision tables themselves
 * are covered by IslandRulesTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IslandStateMachineTest {

    /** Records every show/hide the machine performs, in order. */
    private class RecordingOverlay : OverlayRepository {
        val calls = mutableListOf<String>()
        private val _visible = MutableStateFlow(false)
        override val isPillVisible: StateFlow<Boolean> = _visible
        override fun showPill() { calls += "show"; _visible.value = true }
        override fun hidePill() { calls += "hide"; _visible.value = false }
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var overlay: RecordingOverlay
    private lateinit var scope: CoroutineScope
    private var clock = 100_000L

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        overlay = RecordingOverlay()
        scope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun machine() = IslandStateMachine(
        ShowIslandUseCase(overlay),
        HideIslandUseCase(overlay),
        scope,
    ) { clock }

    // ---- idempotence ----

    @Test fun `repeated active playback shows the pill exactly once`() = runTest(dispatcher) {
        val m = machine()
        repeat(5) { m.onPlaybackChanged(PlaybackState.STATE_PLAYING) }
        runCurrent()
        assertEquals(listOf("show"), overlay.calls)
        assertEquals(IslandState.Pill, m.state.value)
    }

    @Test fun `an unchanged environment is a complete no-op`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PLAYING)
        runCurrent()
        overlay.calls.clear()
        repeat(10) { m.updateEnvironment(targetInForeground = false) }
        runCurrent()
        assertEquals(emptyList<String>(), overlay.calls)
    }

    @Test fun `hiding twice calls the overlay once`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PLAYING)
        runCurrent()
        overlay.calls.clear()
        m.onPlaybackChanged(PlaybackState.STATE_STOPPED)
        m.onPlaybackChanged(PlaybackState.STATE_STOPPED)
        runCurrent()
        assertEquals(listOf("hide"), overlay.calls)
        assertEquals(IslandState.Hidden, m.state.value)
    }

    // ---- environment merging ----

    @Test fun `null parameters leave other gates untouched`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PLAYING)
        runCurrent()
        m.updateEnvironment(targetInForeground = true)   // player came to the front
        runCurrent()
        assertEquals(IslandState.Hidden, m.state.value)
        m.updateEnvironment(stashed = false)             // unrelated gate, no change
        runCurrent()
        assertEquals("targetInForeground must still be set", IslandState.Hidden, m.state.value)
        m.updateEnvironment(targetInForeground = false)  // player left
        runCurrent()
        assertEquals(IslandState.Pill, m.state.value)
    }

    @Test fun `unlocking shows the pill from cached playback with no new media event`() =
        runTest(dispatcher) {
            val m = machine()
            m.onPlaybackChanged(PlaybackState.STATE_PLAYING)
            m.updateEnvironment(unlocked = false)
            runCurrent()
            assertEquals(IslandState.Hidden, m.state.value)
            overlay.calls.clear()
            m.updateEnvironment(unlocked = true)
            runCurrent()
            assertEquals(listOf("show"), overlay.calls)
        }

    @Test fun `unlocking with no session shows nothing`() = runTest(dispatcher) {
        val m = machine()
        m.updateEnvironment(unlocked = false)
        m.updateEnvironment(unlocked = true)
        runCurrent()
        assertEquals(emptyList<String>(), overlay.calls)
        assertEquals(IslandState.Hidden, m.state.value)
    }

    // ---- the auto-hide timer ----

    @Test fun `pause hides after the default timeout`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PAUSED)
        runCurrent()
        assertEquals(IslandState.Pill, m.state.value)
        advanceTimeBy(24_999L); runCurrent()
        assertEquals("must still be showing just before the timeout", IslandState.Pill, m.state.value)
        advanceTimeBy(2L); runCurrent()
        assertEquals(IslandState.Hidden, m.state.value)
    }

    @Test fun `resuming before the timeout disarms it`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PAUSED)
        runCurrent()
        advanceTimeBy(10_000L); runCurrent()
        m.onPlaybackChanged(PlaybackState.STATE_PLAYING)
        runCurrent()
        advanceTimeBy(60_000L); runCurrent()
        assertEquals("the disarmed timer must never fire", IslandState.Pill, m.state.value)
    }

    @Test fun `setPausedTimeout applies to the next timer only`() = runTest(dispatcher) {
        val m = machine()
        m.setPausedTimeout(5_000L)
        m.onPlaybackChanged(PlaybackState.STATE_PAUSED)
        runCurrent()
        advanceTimeBy(5_001L); runCurrent()
        assertEquals(IslandState.Hidden, m.state.value)
    }

    @Test fun `changing the timeout does not re-time a running timer`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PAUSED)   // armed at 25s
        runCurrent()
        m.setPausedTimeout(1_000L)
        advanceTimeBy(1_500L); runCurrent()
        assertEquals("the running timer keeps its original duration", IslandState.Pill, m.state.value)
        advanceTimeBy(24_000L); runCurrent()
        assertEquals(IslandState.Hidden, m.state.value)
    }

    @Test fun `the timer captures its duration when it is armed, not when it starts running`() =
        runTest(dispatcher) {
            val m = machine()
            m.onPlaybackChanged(PlaybackState.STATE_PAUSED)  // armed at 25s
            m.setPausedTimeout(1_000L)                       // before the job body runs at all
            runCurrent()
            advanceTimeBy(1_500L); runCurrent()
            assertEquals(
                "the duration is read when the timer is armed, not when it first resumes",
                IslandState.Pill,
                m.state.value,
            )
        }

    @Test fun `repeated identical environment reports do not keep a paused pill alive`() =
        runTest(dispatcher) {
            val m = machine()
            m.onPlaybackChanged(PlaybackState.STATE_PAUSED)
            runCurrent()
            // The foreground service re-reports the same gates several times a second.
            repeat(20) {
                advanceTimeBy(1_000L); runCurrent()
                m.updateEnvironment(stashed = false)
            }
            advanceTimeBy(5_001L); runCurrent()
            assertEquals(
                "an unchanged environment must not re-evaluate the rules and re-arm the auto-hide",
                IslandState.Hidden,
                m.state.value,
            )
        }

    @Test fun `a second pause restarts the timer rather than stacking one`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PAUSED)
        runCurrent()
        advanceTimeBy(20_000L); runCurrent()
        clock += 20_000L
        m.onPlaybackChanged(PlaybackState.STATE_PAUSED)   // re-reported pause
        runCurrent()
        advanceTimeBy(10_000L); runCurrent()
        assertEquals("the first timer must have been cancelled", IslandState.Pill, m.state.value)
        advanceTimeBy(15_001L); runCurrent()
        assertEquals(IslandState.Hidden, m.state.value)
    }

    // ---- the NONE-after-PAUSED grace window ----

    @Test fun `a session dropped immediately after a pause does not hide the pill`() =
        runTest(dispatcher) {
            val m = machine()
            m.onPlaybackChanged(PlaybackState.STATE_PAUSED)
            runCurrent()
            clock += 300L
            m.onPlaybackChanged(PlaybackState.STATE_NONE)
            runCurrent()
            assertEquals(IslandState.Pill, m.state.value)
        }

    @Test fun `the armed timer does not fire after a graced session drop`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PAUSED)
        runCurrent()
        clock += 300L
        m.onPlaybackChanged(PlaybackState.STATE_NONE)
        runCurrent()
        advanceTimeBy(60_000L); runCurrent()
        assertEquals(
            "the timer re-checks the raw state, which is no longer PAUSED",
            IslandState.Pill,
            m.state.value,
        )
    }

    @Test fun `a session dropped long after a pause hides the pill`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PAUSED)
        runCurrent()
        clock += 5_000L
        m.onPlaybackChanged(PlaybackState.STATE_NONE)
        runCurrent()
        assertEquals(IslandState.Hidden, m.state.value)
    }

    // ---- transient transport states ----

    @Test fun `skipping between tracks leaves the pill alone`() = runTest(dispatcher) {
        val m = machine()
        m.onPlaybackChanged(PlaybackState.STATE_PLAYING)
        runCurrent()
        overlay.calls.clear()
        m.onPlaybackChanged(PlaybackState.STATE_SKIPPING_TO_NEXT)
        runCurrent()
        assertEquals(emptyList<String>(), overlay.calls)
        assertEquals(IslandState.Pill, m.state.value)
    }

    @Test fun `a skip replaces the cached bucket, so unlocking afterwards restores nothing`() =
        runTest(dispatcher) {
            val m = machine()
            m.onPlaybackChanged(PlaybackState.STATE_PLAYING)
            m.onPlaybackChanged(PlaybackState.STATE_SKIPPING_TO_NEXT)
            m.updateEnvironment(unlocked = false)
            runCurrent()
            assertEquals(IslandState.Hidden, m.state.value)
            overlay.calls.clear()
            m.updateEnvironment(unlocked = true)
            runCurrent()
            assertEquals(
                "a transient transport state must overwrite the cached bucket, " +
                    "leaving nothing for the environment rule to restore",
                emptyList<String>(),
                overlay.calls,
            )
        }

    // ---- effect ordering ----

    @Test fun `the overlay is shown before the state flow announces the pill`() =
        runTest(dispatcher) {
            lateinit var m: IslandStateMachine
            var stateWhenShown: IslandState? = null
            val probe = object : OverlayRepository {
                private val _visible = MutableStateFlow(false)
                override val isPillVisible: StateFlow<Boolean> = _visible
                override fun showPill() { stateWhenShown = m.state.value; _visible.value = true }
                override fun hidePill() { _visible.value = false }
            }
            m = IslandStateMachine(ShowIslandUseCase(probe), HideIslandUseCase(probe), scope) { clock }
            m.onPlaybackChanged(PlaybackState.STATE_PLAYING)
            runCurrent()
            assertEquals(
                "the window must already exist when a collector first sees Pill",
                IslandState.Hidden,
                stateWhenShown,
            )
            assertEquals(IslandState.Pill, m.state.value)
        }

    // ---- shutdown ----

    @Test fun `shutdown prevents an armed timer from hiding a later instance`() =
        runTest(dispatcher) {
            val dead = machine()
            dead.onPlaybackChanged(PlaybackState.STATE_PAUSED)
            runCurrent()
            dead.shutdown()
            overlay.calls.clear()
            advanceTimeBy(60_000L); runCurrent()
            assertEquals(
                "a shut-down machine must never touch the overlay again",
                emptyList<String>(),
                overlay.calls,
            )
        }

    @Test fun `a shut-down machine cannot arm a new timer`() = runTest(dispatcher) {
        val dead = machine()
        dead.shutdown()
        dead.onPlaybackChanged(PlaybackState.STATE_PAUSED)
        runCurrent()
        advanceTimeBy(60_000L); runCurrent()
        assertEquals(
            "shutdown cancels the scope, so nothing launched into it can ever run",
            IslandState.Pill,
            dead.state.value,
        )
    }
}
