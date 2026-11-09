package org.telegram.messenger.mzgram.test

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.telegram.ui.mzgram.player.MZGramPlayerQueueSheet
import org.telegram.ui.mzgram.player.MZGramPlayerStyle
import org.telegram.ui.mzgram.player.MZGramPlayerStyleEditor

// The style editor of the music player (the order of the blocks, the
// sliders) and the place of a queue row in the play list.
class MZGramPlayerStyleQueueTest {

    @Test
    fun editor_movesABlock() {
        val order = intArrayOf(MZGramPlayerStyle.BLOCK_PROGRESS, MZGramPlayerStyle.BLOCK_CONTROLS, MZGramPlayerStyle.BLOCK_ACTIONS)
        assertArrayEquals(intArrayOf(1, 0, 2), MZGramPlayerStyleEditor.moved(order, MZGramPlayerStyle.BLOCK_CONTROLS, -1))
        assertArrayEquals(intArrayOf(0, 2, 1), MZGramPlayerStyleEditor.moved(order, MZGramPlayerStyle.BLOCK_CONTROLS, 1))
        // The first block cannot go up, the last one cannot go down
        assertNull(MZGramPlayerStyleEditor.moved(order, MZGramPlayerStyle.BLOCK_PROGRESS, -1))
        assertNull(MZGramPlayerStyleEditor.moved(order, MZGramPlayerStyle.BLOCK_ACTIONS, 1))
        // The order given is not changed
        assertArrayEquals(intArrayOf(0, 1, 2), order)
    }

    @Test
    fun editor_sliderSnapsToTheStep() {
        assertEquals(60, MZGramPlayerStyleEditor.snap(0f, 60, 100, 5))
        assertEquals(100, MZGramPlayerStyleEditor.snap(1f, 60, 100, 5))
        assertEquals(80, MZGramPlayerStyleEditor.snap(0.5f, 60, 100, 5))
        assertEquals(24, MZGramPlayerStyleEditor.snap(0.51f, 0, 48, 2))
        assertEquals(100, MZGramPlayerStyleEditor.snap(1.5f, 60, 100, 5))
    }

    @Test
    fun queue_rowToPlaylist() {
        assertEquals(2, MZGramPlayerQueueSheet.playlistIndex(2, 5, true))
        // Shown upside down: the first row is the last track of the list
        assertEquals(4, MZGramPlayerQueueSheet.playlistIndex(0, 5, false))
        assertEquals(0, MZGramPlayerQueueSheet.playlistIndex(4, 5, false))
    }
}
