// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.data.visibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundTrackerTest {

    private fun res(pkg: String, t: Long) = FgEvent(pkg, resumed = true, timeMs = t)
    private fun pau(pkg: String, t: Long) = FgEvent(pkg, resumed = false, timeMs = t)

    @Test
    fun firstResumeBecomesForeground() {
        val s = resolveForeground(FgState(), listOf(res("player", 1000)), nowMs = 1100)
        assertEquals("player", s.foreground)
    }

    @Test
    fun spuriousLauncherResumeIsIgnoredWhilePlayerStillResumed() {
        // Player is foreground; a launcher resumes in the background 10ms later.
        // The player never pauses -> the launcher resume must be ignored.
        val s = resolveForeground(
            FgState(), listOf(res("player", 1000), res("launcher", 1010)), nowMs = 1500
        )
        assertEquals("player", s.foreground)
    }

    @Test
    fun spuriousCandidateIsDiscardedAfterTolerance() {
        val s = resolveForeground(
            FgState(), listOf(res("player", 1000), res("launcher", 1010)), nowMs = 1000 + 3000
        )
        assertEquals("player", s.foreground)
        assertEquals(null, s.pendingPkg)
    }

    @Test
    fun playerStaysThroughLauncherResumePauseCycles() {
        // Launcher resumes AND pauses in the background repeatedly; player never pauses.
        val s = resolveForeground(
            FgState(foreground = "player"),
            listOf(res("launcher", 1000), pau("launcher", 1500), res("launcher", 2000)),
            nowMs = 5000
        )
        assertEquals("player", s.foreground)
    }

    @Test
    fun genuineSwitchWhenPlayerPausesShortlyAfterLauncherResume() {
        // Launcher resumes, then the player pauses ~0.7s later (real switch to home).
        val s = resolveForeground(
            FgState(foreground = "player"),
            listOf(res("launcher", 1000), pau("player", 1700)),
            nowMs = 2000
        )
        assertEquals("launcher", s.foreground)
    }

    @Test
    fun normalSwitchPauseThenResume() {
        val s = resolveForeground(
            FgState(foreground = "a"), listOf(pau("a", 1000), res("b", 1050)), nowMs = 1500
        )
        assertEquals("b", s.foreground)
    }

    @Test
    fun resumeAfterCurrentAlreadyPausedSwitches() {
        // Current app paused a while ago; a new app resumes much later -> switch.
        val s = resolveForeground(
            FgState(foreground = "a", pausedAtMs = 1000), listOf(res("b", 20_000)), nowMs = 20_100
        )
        assertEquals("b", s.foreground)
    }

    @Test
    fun idleKeepsForeground() {
        val s = resolveForeground(FgState(foreground = "player"), emptyList(), nowMs = 999_999)
        assertEquals("player", s.foreground)
    }

    @Test
    fun reproFromDeviceLog_stuckThenGenuineLeave() {
        // Mirrors the captured stuck stretch on the S21 FE: player resumes, launchers
        // resume in the background for ~36s with no player pause (bug window), then the
        // player finally pauses and a launcher resume lands ~0.7s around it (real leave).
        var s = resolveForeground(FgState(), listOf(res("player", 1538)), nowMs = 1600)
        assertEquals("player", s.foreground)

        s = resolveForeground(s, listOf(res("nova", 1545)), nowMs = 1600) // spurious bg resume
        assertEquals("player", s.foreground)

        s = resolveForeground(s, listOf(pau("nova", 5660)), nowMs = 5700) // launcher's own pause
        assertEquals("player", s.foreground)

        // 24s later, still nothing from the player -> still the player (pill stays hidden).
        s = resolveForeground(s, emptyList(), nowMs = 30_000)
        assertEquals("player", s.foreground)

        // Genuine leave: two launchers resume, player has not paused yet.
        s = resolveForeground(s, listOf(res("samsung", 36_920), res("nova", 37_053)), nowMs = 37_100)
        assertEquals("player", s.foreground)

        // Player pauses ~0.7s after the launcher resume -> switch to the launcher.
        s = resolveForeground(s, listOf(pau("player", 37_777)), nowMs = 37_800)
        assertEquals("nova", s.foreground)
    }
}
