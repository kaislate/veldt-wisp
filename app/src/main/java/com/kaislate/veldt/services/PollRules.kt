package com.kaislate.veldt.services

/**
 * The two questions the foreground poll asks on every tick, lifted out of the service
 * so they can be answered — and asserted — without an Android runtime.
 *
 * Both are total functions of their arguments. Everything expensive, stateful or
 * permission-dependent (reading the usage-event cursor, querying the launcher list,
 * checking the app-op) stays in the caller; what arrives here is already just data.
 *
 * The asymmetry between the two is deliberate and is the thing to preserve. An unknown
 * foreground — no usage access, no events yet, a device that simply has not told us —
 * makes [targetInForeground] answer *no* and [homeOnlyBlocked] also answer *no*. In both
 * cases "we do not know" resolves towards leaving the pill on screen, because the failure
 * we can see (a pill covering the player for one poll period) is cheaper than the failure
 * we cannot (a pill that never appears again and looks like the app is broken).
 */
object PollRules {

    /**
     * Is the app that owns the media session the app the user is currently looking at?
     *
     * True only when all three hold: usage access is granted, some package is actively
     * playing, and that package is the one on screen. The usage-access term is not
     * belt-and-braces — with the permission revoked the caller has no foreground reading
     * at all, and a stale or absent one must never be allowed to read as a match.
     */
    fun targetInForeground(
        hasUsageAccess: Boolean,
        activePackage: String?,
        foregroundPackage: String?,
    ): Boolean =
        hasUsageAccess && activePackage != null && activePackage == foregroundPackage

    /**
     * Does "home screen only" mode forbid the pill right now?
     *
     * True only when the setting is on, the foreground package is known, and it is
     * neither a launcher nor Veldt Wisp's own UI. An unknown foreground fails *open*:
     * a device that never reports usage data would otherwise hide the pill forever with
     * no way for the user to tell why.
     */
    fun homeOnlyBlocked(
        homeOnlyEnabled: Boolean,
        foregroundPackage: String?,
        launcherPackages: Set<String>,
        ownPackage: String,
    ): Boolean =
        homeOnlyEnabled &&
            foregroundPackage != null &&
            foregroundPackage !in launcherPackages &&
            foregroundPackage != ownPackage
}
