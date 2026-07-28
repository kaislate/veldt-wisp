// Copyright (c) 2026 kaislate
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kaislate.veldt.di

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import com.kaislate.veldt.data.overlay.OverlayRepositoryImplementation
import com.kaislate.veldt.data.settings.SettingsRepository
import com.kaislate.veldt.data.visibility.UsageStatsRepository
import com.kaislate.veldt.domain.overlay.HideIslandUseCase
import com.kaislate.veldt.domain.overlay.OverlayRepository
import com.kaislate.veldt.domain.overlay.ShowIslandUseCase
import com.kaislate.veldt.overlay.OverlayWindowManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The application-wide object graph.
 *
 * Every binding here is a singleton, and that is not incidental. The overlay window,
 * the settings store and the two system services all represent something there is
 * exactly one of on the device; handing out a second instance of any of them would
 * mean two pieces of the app disagreeing about the state of one thing — two pill
 * windows, or a settings write that another reader never sees.
 *
 * Bindings fall into three groups:
 *
 *  * **repositories** — constructed against the application context, never an
 *    Activity's, since they outlive every screen;
 *  * **system services** — pulled out of the platform so that callers take a typed
 *    dependency instead of repeating a cast against a string constant;
 *  * **overlay chain** — window manager, then the repository that fronts it, then the
 *    two use cases that are all the rest of the app is allowed to say to it. Each
 *    layer only ever sees the one below, which is why they are wired here in order
 *    rather than letting callers reach past a layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ---- Repositories ---------------------------------------------------------

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext ctx: Context): SettingsRepository =
        SettingsRepository(ctx)

    @Provides
    @Singleton
    fun provideUsageStatsRepository(@ApplicationContext ctx: Context): UsageStatsRepository =
        UsageStatsRepository(ctx)

    // ---- Platform services ----------------------------------------------------

    @Provides
    @Singleton
    fun provideKeyguardManager(@ApplicationContext ctx: Context): KeyguardManager =
        ctx.getSystemService(KeyguardManager::class.java)

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext ctx: Context): NotificationManager =
        ctx.getSystemService(NotificationManager::class.java)

    // ---- The overlay chain, bottom up -----------------------------------------

    @Provides
    @Singleton
    fun provideOverlayWindowManager(
        @ApplicationContext ctx: Context,
        settings: SettingsRepository,
    ): OverlayWindowManager = OverlayWindowManager(ctx, settings)

    @Provides
    @Singleton
    fun provideOverlayRepository(windows: OverlayWindowManager): OverlayRepository =
        OverlayRepositoryImplementation(windows)

    @Provides
    @Singleton
    fun provideShowIslandUseCase(overlay: OverlayRepository): ShowIslandUseCase =
        ShowIslandUseCase(overlay)

    @Provides
    @Singleton
    fun provideHideIslandUseCase(overlay: OverlayRepository): HideIslandUseCase =
        HideIslandUseCase(overlay)
}
