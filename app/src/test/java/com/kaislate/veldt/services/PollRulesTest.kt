package com.kaislate.veldt.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollRulesTest {

    private val launchers = setOf("com.oem.launcher", "com.android.launcher3")
    private val self = "com.kaislate.veldt"

    // ---- targetInForeground ----

    @Test fun `the playing app being on screen counts as foreground`() {
        assertTrue(PollRules.targetInForeground(true, "com.player", "com.player"))
    }

    @Test fun `a different app on screen does not`() {
        assertFalse(PollRules.targetInForeground(true, "com.player", "com.other"))
    }

    @Test fun `without usage access we never claim the target is in front`() {
        assertFalse(PollRules.targetInForeground(false, "com.player", "com.player"))
    }

    @Test fun `with no active session we never claim the target is in front`() {
        assertFalse(PollRules.targetInForeground(true, null, null))
        assertFalse(PollRules.targetInForeground(true, null, "com.player"))
    }

    @Test fun `an unknown foreground does not match an active session`() {
        assertFalse(PollRules.targetInForeground(true, "com.player", null))
    }

    // ---- homeOnlyBlocked ----

    @Test fun `home-only blocks when a normal app is on screen`() {
        assertTrue(PollRules.homeOnlyBlocked(true, "com.other", launchers, self))
    }

    @Test fun `home-only permits on a launcher`() {
        assertFalse(PollRules.homeOnlyBlocked(true, "com.oem.launcher", launchers, self))
        assertFalse(PollRules.homeOnlyBlocked(true, "com.android.launcher3", launchers, self))
    }

    @Test fun `home-only permits over our own settings screen`() {
        assertFalse(PollRules.homeOnlyBlocked(true, self, launchers, self))
    }

    @Test fun `home-only never blocks when the setting is off`() {
        assertFalse(PollRules.homeOnlyBlocked(false, "com.other", launchers, self))
    }

    @Test fun `an unknown foreground fails open rather than hiding forever`() {
        assertFalse(PollRules.homeOnlyBlocked(true, null, launchers, self))
    }

    @Test fun `an empty launcher set still permits our own package`() {
        assertFalse(PollRules.homeOnlyBlocked(true, self, emptySet(), self))
        assertTrue(PollRules.homeOnlyBlocked(true, "com.other", emptySet(), self))
    }
}
