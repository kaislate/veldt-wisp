package com.kaislate.veldt.domain.visibility

import com.kaislate.veldt.data.visibility.UsageStatsRepository
import javax.inject.Inject

/**
 * Answers whether a given package is the one the user is currently looking at.
 *
 * Used to decide that the pill would be redundant: there is no point floating a
 * now-playing card over the player that is already filling the screen.
 *
 * The lookup itself, and everything that can go wrong with it — usage access being
 * revoked, the query window turning up empty — belongs to [UsageStatsRepository]; this
 * is only the phrasing of the question.
 */
class IsTargetAppInForegroundUseCase @Inject constructor(
    private val repo: UsageStatsRepository
) {
    operator fun invoke(pkg: String) = repo.isAppInForeground(pkg)
}
