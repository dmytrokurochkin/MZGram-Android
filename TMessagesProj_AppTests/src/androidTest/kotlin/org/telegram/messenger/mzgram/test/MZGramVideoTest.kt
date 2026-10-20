package org.telegram.messenger.mzgram.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.telegram.messenger.mzgram.MZGramConfig
import org.telegram.ui.Components.VideoPlayer
import org.telegram.ui.PhotoViewer

// Interface > Auto pause video and Media > Original quality on Telegram 13.0,
// whose VideoPlayer plays through media3 (it replaced ExoPlayer 2).
class MZGramVideoTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    // A real VideoPlayer (its media3 track selector and data sources are
    // created) that reports playing without a video to play.
    private class Player : VideoPlayer(false, true) {
        var playing = true
        override fun isPlaying() = playing
        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun seekTo(positionMs: Long) {}
        override fun getCurrentPosition() = 0L
    }

    private fun withViewerPlaying(autoPause: Boolean, check: (PhotoViewer, Player) -> Unit) {
        val before = MZGramConfig.autoPauseVideo
        MZGramConfig.autoPauseVideo = autoPause
        val field = PhotoViewer::class.java.getDeclaredField("videoPlayer").apply { isAccessible = true }
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val viewer = PhotoViewer.getInstance()
            val saved = field.get(viewer)
            val player = Player()
            try {
                field.set(viewer, player)
                check(viewer, player)
            } catch (e: Throwable) {
                failure = e
            } finally {
                field.set(viewer, saved)
                MZGramConfig.autoPauseVideo = before
            }
        }
        failure?.let { throw it }
    }

    @Test
    fun autoPause_on_pausesWhenTheAppLeavesAndPlaysOnReturn() {
        withViewerPlaying(autoPause = true) { viewer, player ->
            viewer.onPause()
            assertFalse("still playing in the background", player.playing)
            viewer.onResume()
            assertTrue("not playing again on return", player.playing)
        }
    }

    @Test
    fun autoPause_off_keepsPlayingAsTelegramDoes() {
        withViewerPlaying(autoPause = false) { viewer, player ->
            viewer.onPause()
            assertTrue("paused with the switch off", player.playing)
        }
    }

    private fun uri(original: Boolean, width: Int, height: Int) = VideoPlayer.VideoUri().apply {
        this.original = original
        this.width = width
        this.height = height
        codec = "avc"
        bitrate = width * height.toDouble()
    }

    @Test
    fun originalQuality_playsTheOriginalOnlyWithTheSwitchOn() {
        val before = MZGramConfig.preferOriginalQuality
        try {
            val original = uri(true, 1920, 1080)
            val smaller = uri(false, 640, 360)
            val qualities = arrayListOf(VideoPlayer.Quality(original), VideoPlayer.Quality(smaller))

            MZGramConfig.preferOriginalQuality = true
            assertEquals(original, VideoPlayer.getQualityForPlayer(qualities))
            val saved = VideoPlayer.getSavedQuality(qualities, null)
            assertNotNull("no saved quality with the switch on", saved)
            assertTrue("the saved quality is not the original", saved!!.original)

            MZGramConfig.preferOriginalQuality = false
            assertFalse("the original without the switch and not downloaded", VideoPlayer.getQualityForPlayer(qualities)!!.original)
        } finally {
            MZGramConfig.preferOriginalQuality = before
        }
    }
}
