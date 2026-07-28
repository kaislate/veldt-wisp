// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves the virtual-time harness the state-machine tests depend on: a coroutine
 * launched on Dispatchers.Main can be advanced without real waiting. If this test
 * hangs or fails, every timing test that builds on this harness is meaningless.
 *
 * Note on [advanceTimeBy]: since coroutines 1.7 it runs only the tasks scheduled
 * *strictly before* the new virtual time, so advancing by exactly the delay does
 * not fire it. Hence the 24_999 / +2 straddle below rather than a flat 25_000.
 * Tests that assert "fires at exactly T" must advance past T, not to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HarnessSmokeTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `virtual time advances a delay on the main dispatcher`() = runTest(dispatcher) {
        var fired = false
        val job = CoroutineScope(Dispatchers.Main).launch {
            delay(25_000L)
            fired = true
        }
        advanceTimeBy(24_999L)
        runCurrent()
        assertFalse("must not fire early", fired)
        advanceTimeBy(2L)
        runCurrent()
        assertTrue("must fire after the delay", fired)
        job.cancel()
    }
}
