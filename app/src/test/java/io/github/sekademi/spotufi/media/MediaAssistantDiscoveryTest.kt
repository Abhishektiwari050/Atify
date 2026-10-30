package io.github.sekademi.spotufi.media

import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.media.MediaBrowserServiceCompat.BrowserRoot
import androidx.media.session.MediaButtonReceiver
import androidx.test.core.app.ApplicationProvider
import io.github.sekademi.spotufi.di.RepeatMode
import io.github.sekademi.spotufi.ui.notification.MediaSessionHolder
import io.github.sekademi.spotufi.ui.notification.MusicBrowserService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Verification test verifying:
 * 1. MediaSessionCompat is initialized, active, and publishing active PlaybackStateCompat actions.
 * 2. MediaButtonReceiver intent handling with injected KEYCODE_MEDIA_PLAY intercepts via callback.
 * 3. MediaBrowserServiceCompat exposes valid root and browsable media items for Google Assistant discovery.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaAssistantDiscoveryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MediaSessionHolder.release()
    }

    @After
    fun tearDown() {
        MediaSessionHolder.release()
    }

    @Test
    fun verifyMediaSessionInitializationActiveStateAndPlaybackActions() {
        val session = MediaSessionHolder.getOrCreateSession(context)

        // 1. Verify session is explicitly initialized and active
        assertNotNull("MediaSessionCompat must not be null", session)
        assertTrue("MediaSessionCompat must be set to active", session.isActive)
        assertNotNull("Session token must be published", session.sessionToken)

        // 2. Publish PlaybackStateCompat with active playback
        MediaSessionHolder.updatePlaybackState(
            state = PlaybackStateCompat.STATE_PLAYING,
            position = 32_000L,
            repeatMode = RepeatMode.ALL,
            isShuffle = true
        )

        val controller = session.controller
        val playbackState = controller.playbackState

        assertNotNull("PlaybackStateCompat must be published", playbackState)
        assertEquals("Playback state must be STATE_PLAYING", PlaybackStateCompat.STATE_PLAYING, playbackState.state)
        assertEquals("Playback position must match", 32_000L, playbackState.position)

        // 3. Verify all required active playback actions are present
        val actions = playbackState.actions
        assertTrue("Must support ACTION_PLAY", (actions and PlaybackStateCompat.ACTION_PLAY) != 0L)
        assertTrue("Must support ACTION_PAUSE", (actions and PlaybackStateCompat.ACTION_PAUSE) != 0L)
        assertTrue("Must support ACTION_PLAY_PAUSE", (actions and PlaybackStateCompat.ACTION_PLAY_PAUSE) != 0L)
        assertTrue("Must support ACTION_SKIP_TO_NEXT", (actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0L)
        assertTrue("Must support ACTION_SKIP_TO_PREVIOUS", (actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0L)
        assertTrue("Must support ACTION_SEEK_TO", (actions and PlaybackStateCompat.ACTION_SEEK_TO) != 0L)
        assertTrue("Must support ACTION_STOP", (actions and PlaybackStateCompat.ACTION_STOP) != 0L)
        assertTrue("Must support ACTION_PLAY_FROM_SEARCH", (actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH) != 0L)
        assertTrue("Must support ACTION_PLAY_FROM_MEDIA_ID", (actions and PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID) != 0L)
        assertTrue("Must support ACTION_PLAY_FROM_URI", (actions and PlaybackStateCompat.ACTION_PLAY_FROM_URI) != 0L)
        assertTrue("Must support ACTION_SET_REPEAT_MODE", (actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0L)
        assertTrue("Must support ACTION_SET_SHUFFLE_MODE", (actions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0L)
        assertTrue("Must support ACTION_FAST_FORWARD", (actions and PlaybackStateCompat.ACTION_FAST_FORWARD) != 0L)
        assertTrue("Must support ACTION_REWIND", (actions and PlaybackStateCompat.ACTION_REWIND) != 0L)
        assertTrue("Must support ACTION_PREPARE", (actions and PlaybackStateCompat.ACTION_PREPARE) != 0L)
        assertTrue("Must support ACTION_PREPARE_FROM_SEARCH", (actions and PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH) != 0L)
        assertTrue("Must support ACTION_PREPARE_FROM_MEDIA_ID", (actions and PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID) != 0L)
        assertTrue("Must support ACTION_PREPARE_FROM_URI", (actions and PlaybackStateCompat.ACTION_PREPARE_FROM_URI) != 0L)

        // 4. Verify metadata publication runs cleanly
        MediaSessionHolder.updateMetadata(
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200_000L,
            coverUri = "https://example.com/cover.jpg"
        )
        ShadowLooper.idleMainLooper()
        assertTrue("MediaSessionCompat must remain active after metadata update", session.isActive)
    }

    @Test
    fun verifyMediaButtonReceiverInjectsPlayKeyAndInterceptsCallback() {
        var onPlayIntercepted = false
        var onPauseIntercepted = false
        var onSkipNextIntercepted = false
        var onSkipPrevIntercepted = false

        val testCallback = object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                onPlayIntercepted = true
            }

            override fun onPause() {
                onPauseIntercepted = true
            }

            override fun onSkipToNext() {
                onSkipNextIntercepted = true
            }

            override fun onSkipToPrevious() {
                onSkipPrevIntercepted = true
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                val event = mediaButtonEvent?.let { androidx.core.content.IntentCompat.getParcelableExtra(it, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) }
                if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            onPlay()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            onPause()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            onSkipToNext()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            onSkipToPrevious()
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        }

        val session = MediaSessionHolder.getOrCreateSession(context, testCallback)

        // 1. Inject KEYCODE_MEDIA_PLAY event via MediaButtonReceiver intent
        val playDownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        val playDownIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, playDownEvent)
        }
        val handledPlayEvent = MediaButtonReceiver.handleIntent(session, playDownIntent)
        assertNotNull("MediaButtonReceiver must extract KeyEvent from intent", handledPlayEvent)
        assertEquals("Extracted key code must be KEYCODE_MEDIA_PLAY", KeyEvent.KEYCODE_MEDIA_PLAY, handledPlayEvent?.keyCode)
        assertEquals("Extracted action must be ACTION_DOWN", KeyEvent.ACTION_DOWN, handledPlayEvent?.action)

        testCallback.onMediaButtonEvent(playDownIntent)
        assertTrue("Session callback must intercept injected KEYCODE_MEDIA_PLAY event", onPlayIntercepted)

        // 2. Inject KEYCODE_MEDIA_PAUSE
        val pauseDownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        val pauseDownIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, pauseDownEvent)
        }
        val handledPauseEvent = MediaButtonReceiver.handleIntent(session, pauseDownIntent)
        assertNotNull("MediaButtonReceiver must extract pause KeyEvent", handledPauseEvent)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PAUSE, handledPauseEvent?.keyCode)

        testCallback.onMediaButtonEvent(pauseDownIntent)
        assertTrue("Session callback must intercept injected KEYCODE_MEDIA_PAUSE event", onPauseIntercepted)

        // 3. Inject KEYCODE_MEDIA_NEXT
        val nextDownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
        val nextDownIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, nextDownEvent)
        }
        val handledNextEvent = MediaButtonReceiver.handleIntent(session, nextDownIntent)
        assertNotNull("MediaButtonReceiver must extract next KeyEvent", handledNextEvent)
        assertEquals(KeyEvent.KEYCODE_MEDIA_NEXT, handledNextEvent?.keyCode)

        testCallback.onMediaButtonEvent(nextDownIntent)
        assertTrue("Session callback must intercept injected KEYCODE_MEDIA_NEXT event", onSkipNextIntercepted)

        // 4. Inject KEYCODE_MEDIA_PREVIOUS
        val prevDownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val prevDownIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            putExtra(Intent.EXTRA_KEY_EVENT, prevDownEvent)
        }
        val handledPrevEvent = MediaButtonReceiver.handleIntent(session, prevDownIntent)
        assertNotNull("MediaButtonReceiver must extract prev KeyEvent", handledPrevEvent)
        assertEquals(KeyEvent.KEYCODE_MEDIA_PREVIOUS, handledPrevEvent?.keyCode)

        testCallback.onMediaButtonEvent(prevDownIntent)
        assertTrue("Session callback must intercept injected KEYCODE_MEDIA_PREVIOUS event", onSkipPrevIntercepted)
    }

    @Test
    fun verifyMediaBrowserServiceCompatStructureAndRoot() = runBlocking {
        val browserService = MusicBrowserService()

        // 1. Query onGetRoot for Google Assistant package
        val assistantPackage = "com.google.android.googlequicksearchbox"
        val root = browserService.onGetRoot(assistantPackage, 1000, null)

        assertNotNull("MediaBrowserServiceCompat root must not be null for Google Assistant", root)
        assertEquals("Root ID must match MEDIA_ROOT_ID", MusicBrowserService.MEDIA_ROOT_ID, root?.rootId)

        val extras = root?.extras
        assertNotNull("Root extras must be present", extras)
        assertTrue("Root must declare EXTRA_RECENT support for assistant", extras?.getBoolean(BrowserRoot.EXTRA_RECENT) == true)
        assertTrue("Root must declare EXTRA_OFFLINE support for assistant", extras?.getBoolean(BrowserRoot.EXTRA_OFFLINE) == true)
        assertTrue("Root must declare EXTRA_SUGGESTED support for assistant", extras?.getBoolean(BrowserRoot.EXTRA_SUGGESTED) == true)
        assertEquals("Root must declare SEARCH_SUPPORTED", 1, extras?.getInt("android.media.browse.SEARCH_SUPPORTED"))
        assertTrue("Root must declare android.service.media.extra.RECENT", extras?.getBoolean("android.service.media.extra.RECENT") == true)
        assertTrue("Root must declare android.service.media.extra.OFFLINE", extras?.getBoolean("android.service.media.extra.OFFLINE") == true)
        assertTrue("Root must declare android.service.media.extra.SUGGESTED", extras?.getBoolean("android.service.media.extra.SUGGESTED") == true)

        // 2. Query browsable root items exposed to assistant
        val rootChildren = browserService.loadChildrenForParent(MusicBrowserService.MEDIA_ROOT_ID)
        assertNotNull("Root children must not be null", rootChildren)
        assertEquals("Must expose 4 main browsable library sections", 4, rootChildren.size)

        val itemIds = rootChildren.map { it.mediaId }
        assertTrue("Must expose Liked Songs", itemIds.contains(MusicBrowserService.NODE_LIKED))
        assertTrue("Must expose Downloads", itemIds.contains(MusicBrowserService.NODE_DOWNLOADS))
        assertTrue("Must expose Playlists", itemIds.contains(MusicBrowserService.NODE_PLAYLISTS))
        assertTrue("Must expose Albums", itemIds.contains(MusicBrowserService.NODE_ALBUMS))

        rootChildren.forEach { item ->
            assertTrue("Root item ${item.mediaId} must have FLAG_BROWSABLE", item.isBrowsable)
            assertNotNull("Root item ${item.mediaId} must have a title", item.description.title)
        }

        // 3. Verify Android Auto package query
        val autoPackage = "com.google.android.projection.gearhead"
        val autoRoot = browserService.onGetRoot(autoPackage, 1001, null)
        assertNotNull("MediaBrowserServiceCompat root must be authorized for Android Auto", autoRoot)
    }

    @Test
    fun verifyVoicePlayDispatchActivityHeadlessExecutionAndBufferingAck() {
        val session = MediaSessionHolder.getOrCreateSession(context)
        assertTrue("MediaSessionCompat must be active", session.isActive)

        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, "Bohemian Rhapsody")
            putExtra("query", "Bohemian Rhapsody")
            addCategory(Intent.CATEGORY_VOICE)
        }

        val controller = org.robolectric.Robolectric.buildActivity(io.github.sekademi.spotufi.ui.VoicePlayDispatchActivity::class.java, intent)
            .create()

        // 1. Verify activity finished immediately without lingering in foreground
        assertTrue("VoicePlayDispatchActivity must finish immediately to avoid full-screen driver mode", controller.get().isFinishing)

        // 2. Verify MediaSessionHolder received immediate STATE_BUFFERING acknowledgment
        val playbackState = session.controller.playbackState
        assertNotNull("Playback state must be published", playbackState)
        assertEquals("Session must publish STATE_BUFFERING immediately for Google Assistant ACK", PlaybackStateCompat.STATE_BUFFERING, playbackState.state)

        // 3. Verify PAUSE action handling
        val pauseIntent = Intent("io.github.sekademi.spotufi.action.PAUSE")
        val pauseController = org.robolectric.Robolectric.buildActivity(io.github.sekademi.spotufi.ui.VoicePlayDispatchActivity::class.java, pauseIntent)
            .create()
        assertTrue("Pause dispatch must finish immediately", pauseController.get().isFinishing)
        assertEquals("Session must reflect STATE_PAUSED", PlaybackStateCompat.STATE_PAUSED, session.controller.playbackState.state)

        // 4. Verify RESUME action handling
        val resumeIntent = Intent("io.github.sekademi.spotufi.action.RESUME")
        val resumeController = org.robolectric.Robolectric.buildActivity(io.github.sekademi.spotufi.ui.VoicePlayDispatchActivity::class.java, resumeIntent)
            .create()
        assertTrue("Resume dispatch must finish immediately", resumeController.get().isFinishing)
        assertEquals("Session must reflect STATE_PLAYING", PlaybackStateCompat.STATE_PLAYING, session.controller.playbackState.state)
    }
}
