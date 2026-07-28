package com.kaislate.veldt.services

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.PlaybackState
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kaislate.veldt.MainActivity
import com.kaislate.veldt.R
import com.kaislate.veldt.data.media.MediaSessionBus
import com.kaislate.veldt.data.settings.SettingsRepository
import com.kaislate.veldt.data.visibility.UsageStatsRepository
import com.kaislate.veldt.overlay.IslandState
import com.kaislate.veldt.overlay.IslandStateMachine
import com.kaislate.veldt.util.Constants
import com.kaislate.veldt.util.PermissionsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The process that keeps the island alive.
 *
 * Two things here are worth understanding before changing anything.
 *
 * **The service outlives the pill.** It stays in the foreground while the island is
 * hidden, as long as a media session is still loaded — paused counts. Nothing else in
 * the app watches for the user leaving the player or unlocking the phone, so a service
 * that shut down whenever the pill went away would be a service the pill could never
 * come back from. The mirror of that rule matters too: with no session at all there is
 * nothing left to watch, so the service retires rather than sit on a permanent
 * foreground notification.
 *
 * **The foreground reading is consumed, not polled.**
 * [UsageStatsRepository.latestForegroundPackage] reads a cursor over the system's usage
 * events and advances it. Every call consumes the events since the last one; the reading
 * it returns is the fold of that batch onto everything seen before. That gives it two
 * properties an ordinary getter does not have, and both are load-bearing:
 *
 *  * Call it twice in one tick and the second call folds an empty batch against a fresh
 *    `now`, which collapses the tolerance window that suppresses spurious background
 *    resumes. So it is called **exactly once** per poll iteration — see [readEnvironment].
 *  * Call it without usage access and it consumes the batch, gets nothing back, and
 *    still moves the cursor. Every app switch made while the permission was off is then
 *    lost for good. So it is called **only** when [PermissionsHelper.hasUsageAccess]
 *    says the permission is live, and the permission is re-checked every tick because
 *    the user can revoke it from system settings at any moment.
 *
 * Getting either wrong looks, from the outside, like the pill reappearing on top of the
 * app that is playing — the failure this poll exists to avoid. This is the only caller
 * of [UsageStatsRepository.latestForegroundPackage] in the app, and it should stay that
 * way; a second caller anywhere would steal events from this one.
 */
@AndroidEntryPoint
class IslandForegroundService : Service() {

    @Inject lateinit var stateMachine: IslandStateMachine
    @Inject lateinit var usageStats: UsageStatsRepository
    @Inject lateinit var keyguard: KeyguardManager
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifications: NotificationManager

    // No OverlayWindowManager field: it is an AppModule @Singleton that Hilt must already
    // have built to satisfy stateMachine (IslandStateMachine <- ShowIslandUseCase <-
    // OverlayRepository <- OverlayWindowManager), so injecting it here would not change
    // when it is constructed. See the task report for the full argument.

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null
    private var stateJob: Job? = null

    /** Mirrors the island-enabled setting; optimistic until DataStore's first emission. */
    private var islandEnabled = true

    /** Read by the settings observer off the main thread. */
    @Volatile private var minimiseNotification = false

    /** Read by the poll, written by the settings observer. */
    @Volatile private var homeOnly = false

    /** The last raw `PlaybackState.STATE_*` the bus reported, or null for no session. */
    private var lastPlayback: Int? = null

    // What the poll last told the state machine. Null means "nothing reported yet", so
    // the first iteration always reports, whatever it finds.
    private var reportedTargetInForeground: Boolean? = null
    private var reportedHomeOnlyBlocked: Boolean? = null

    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d(TAG, "screen broadcast: $action")
            when (action) {
                Intent.ACTION_USER_PRESENT ->
                    stateMachine.updateEnvironment(unlocked = true)

                // Not redundant with USER_PRESENT: a device with no lockscreen, or one
                // whose swipe lock the user already dismissed, never sends USER_PRESENT
                // at all. Without this branch the island would stay dead after every
                // screen-off until something restarted the service. The keyguard test is
                // what keeps it from firing while the lockscreen is still up.
                Intent.ACTION_SCREEN_ON ->
                    if (!keyguard.isKeyguardLocked) stateMachine.updateEnvironment(unlocked = true)

                Intent.ACTION_SCREEN_OFF ->
                    stateMachine.updateEnvironment(unlocked = false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Channels first, then foreground, and nothing between them: the system kills a
        // service that does not post its notification promptly after startForegroundService,
        // and a notification on a channel that does not exist yet is silently dropped.
        prepareChannels()
        startForeground(Constants.NOTIF_ID, buildOngoingNotification())

        watchSettings()
        watchPlayback()
        registerScreenReceiver()
        startPolling()
        // Last, so the observers above have already seeded the playback state the
        // collector's very first emission is judged against.
        followIslandState()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STASH -> {
                stateMachine.updateEnvironment(stashed = true)
                notifications.notify(STASH_NOTIF_ID, buildStashNotification())
            }

            ACTION_UNSTASH -> {
                notifications.cancel(STASH_NOTIF_ID)
                stateMachine.updateEnvironment(stashed = false)
            }
        }
        // Deliberately not sticky: MediaNotificationListener restarts us the moment a
        // session appears, which is the only moment there is anything to do.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // First, and it must stay first. This class is recreated on every restart, and so
        // is its @ServiceScoped state machine. An armed auto-hide belonging to the machine
        // we are abandoning would otherwise fire ~25 s from now against the *singleton*
        // overlay and pull the next instance's pill off the screen.
        stateMachine.shutdown()
        pollJob?.cancel()
        stateJob?.cancel()
        unregisterScreenReceiver()
        notifications.cancel(STASH_NOTIF_ID)
        scope.cancel()
        super.onDestroy()
    }

    // ---- settings and session observers ----

    private fun watchSettings() {
        scope.launch {
            settings.islandEnabledFlow.collect { enabled ->
                islandEnabled = enabled
                stateMachine.updateEnvironment(enabled = enabled)
                Log.d(TAG, "island enabled: $enabled")
                if (enabled) {
                    // Deliberate nudge, not redundancy. The bus is a StateFlow, so a
                    // subscriber that is already current gets nothing more from it. A user
                    // who switches the island on while music is already playing would see
                    // no pill until the next transport event — which for a track ten
                    // minutes long is a long wait — unless we re-feed what it already holds.
                    stateMachine.onPlaybackChanged(MediaSessionBus.playbackState.value)
                } else {
                    // Off means off: overlay hidden by the gate above, notification gone,
                    // service gone.
                    notifications.cancel(STASH_NOTIF_ID)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        scope.launch {
            settings.hideDelayMsFlow.collect { stateMachine.setPausedTimeout(it) }
        }

        scope.launch {
            settings.hideNotificationFlow.collect { minimise ->
                if (minimise == minimiseNotification) return@collect
                minimiseNotification = minimise
                // A channel's importance is fixed once created, so the only way to move
                // between a visible and a minimised notification is to re-post the same
                // id on the other channel. Failing that is cosmetic; taking the service
                // down over it would not be.
                runCatching { startForeground(Constants.NOTIF_ID, buildOngoingNotification()) }
                    .onFailure { Log.w(TAG, "could not re-post the service notification", it) }
            }
        }

        scope.launch {
            settings.homeOnlyFlow.collect { homeOnly = it }
        }
    }

    private fun watchPlayback() {
        scope.launch {
            MediaSessionBus.playbackState.collect { raw ->
                lastPlayback = raw
                Log.d(TAG, "playback state: $raw")
                stateMachine.onPlaybackChanged(raw)
            }
        }
    }

    // ---- screen and keyguard ----

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        // Guarded anyway: unregistering one the framework never took throws.
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    // ---- the foreground poll ----

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                readEnvironment()
                delay(POLL_PERIOD_MS)
            }
        }
    }

    /**
     * One tick. Reads the world once, asks [PollRules] the two questions, and tells the
     * state machine only when an answer has actually changed — the machine is cheap to
     * call but the log is not, and at this cadence a line per tick buries everything else.
     */
    private fun readEnvironment() {
        // Re-read rather than cache: usage access can be revoked from system settings
        // while we are running, and the consequences of acting on a stale "yes" are
        // exactly what the class KDoc describes.
        val usageAccess = PermissionsHelper.hasUsageAccess(this)
        val playingPackage = MediaSessionBus.activePackage.value
        // The one and only call per tick, and never without the permission.
        val onScreen = if (usageAccess) usageStats.latestForegroundPackage() else null

        val targetInForeground = PollRules.targetInForeground(usageAccess, playingPackage, onScreen)
        val homeOnlyBlocked = PollRules.homeOnlyBlocked(
            homeOnlyEnabled = homeOnly,
            foregroundPackage = onScreen,
            launcherPackages = usageStats.launcherPackages(),
            ownPackage = packageName,
        )

        if (targetInForeground == reportedTargetInForeground && homeOnlyBlocked == reportedHomeOnlyBlocked) return

        Log.i(
            TAG,
            "poll change: onScreen=$onScreen playing=$playingPackage " +
                "targetInForeground=$targetInForeground homeOnlyBlocked=$homeOnlyBlocked",
        )
        reportedTargetInForeground = targetInForeground
        reportedHomeOnlyBlocked = homeOnlyBlocked
        stateMachine.updateEnvironment(
            targetInForeground = targetInForeground,
            homeOnlyBlocked = homeOnlyBlocked,
        )
    }

    // ---- lifetime ----

    private fun followIslandState() {
        stateJob?.cancel()
        stateJob = scope.launch {
            stateMachine.state.collect { state ->
                val sessionAlive = hasLiveSession()
                Log.d(TAG, "island state: $state (session alive: $sessionAlive, enabled: $islandEnabled)")
                when (state) {
                    IslandState.Pill, IslandState.Expanded ->
                        notifications.notify(Constants.NOTIF_ID, buildOngoingNotification())

                    IslandState.Hidden ->
                        if (islandEnabled && sessionAlive) {
                            // Hidden but watching: this is the case the service exists for.
                            notifications.notify(Constants.NOTIF_ID, buildOngoingNotification())
                        } else {
                            Log.d(TAG, "nothing left to watch; stopping")
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                }
            }
        }
    }

    /**
     * Whether there is still a session worth staying alive for. Paused counts: the user
     * has music loaded and is more likely to resume it than to abandon it.
     */
    private fun hasLiveSession(): Boolean = when (lastPlayback) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_PAUSED -> true

        else -> false
    }

    // ---- notifications ----

    private fun prepareChannels() {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_VISIBLE, "Veldt Wisp", NotificationManager.IMPORTANCE_LOW)
        )
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_HIDDEN, "Veldt Wisp (minimized)", NotificationManager.IMPORTANCE_MIN)
        )
        // Retired in favour of the two above. Left behind it would sit in the user's
        // notification settings forever with nothing ever posted to it.
        notifications.deleteNotificationChannel(Constants.NOTIF_CHANNEL_ID)
    }

    private fun buildOngoingNotification() = NotificationCompat.Builder(
        this,
        if (minimiseNotification) CHANNEL_HIDDEN else CHANNEL_VISIBLE,
    )
        .setSmallIcon(R.drawable.ic_stat_pill)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Tap to open Veldt Wisp settings")
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                REQUEST_OPEN_SETTINGS,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .setOngoing(true)
        .setSilent(true)
        .build()

    private fun buildStashNotification() = NotificationCompat.Builder(this, CHANNEL_VISIBLE)
        .setSmallIcon(R.drawable.ic_stat_pill)
        .setContentTitle("Veldt Wisp is hidden")
        .setContentText("Tap to bring the island back")
        .setContentIntent(
            PendingIntent.getService(
                this,
                REQUEST_UNSTASH,
                Intent(this, IslandForegroundService::class.java).setAction(ACTION_UNSTASH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
        .setOngoing(false)
        .setAutoCancel(true)
        .build()

    companion object {
        private const val TAG = "IslandForegroundService"

        /**
         * How often the foreground is re-read. 700 ms is a compromise: the user notices a
         * pill that takes much longer than this to get out of the way of the app they just
         * opened, and every extra tick costs a usage-events query and a log line. Raising
         * it towards 1000 ms buys back CPU and log volume at the cost of that responsiveness.
         */
        private const val POLL_PERIOD_MS = 700L

        private const val REQUEST_UNSTASH = 2
        private const val REQUEST_OPEN_SETTINGS = 3

        const val ACTION_STASH = "com.kaislate.veldt.STASH"
        const val ACTION_UNSTASH = "com.kaislate.veldt.UNSTASH"

        const val STASH_NOTIF_ID = 1002

        // Channel ids are permanent: a user's per-channel notification settings are keyed
        // on them, so renaming one silently resets everyone's preferences on upgrade.
        const val CHANNEL_VISIBLE = "veldt_fgs"
        const val CHANNEL_HIDDEN = "veldt_fgs_min"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, IslandForegroundService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, IslandForegroundService::class.java))
        }
    }
}
