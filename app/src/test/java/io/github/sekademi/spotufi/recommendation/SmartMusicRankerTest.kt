package io.github.sekademi.spotufi.recommendation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.sekademi.spotufi.data.entity.SongsModel
import io.github.sekademi.spotufi.data.recommendation.SmartMusicRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartMusicRankerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun verifyExactTitleMatchRankedFirst() {
        val songs = listOf(
            SongsModel(id = 1, title = "Starboy Remix", album = "Remixes", singer = "Kygo", coverUri = "", url = ""),
            SongsModel(id = 2, title = "Starboy", album = "Starboy", singer = "The Weeknd", coverUri = "", url = ""),
            SongsModel(id = 3, title = "I Feel It Coming", album = "Starboy", singer = "The Weeknd", coverUri = "", url = ""),
        )

        val reranked = SmartMusicRanker.rerankSearchResults(context, songs, "Starboy")
        assertEquals(2, reranked.first().id)
        assertEquals("Starboy", reranked.first().title)
    }

    @Test
    fun verifyMultiWordOutOfOrderTokensMatchCorrectSong() {
        val songs = listOf(
            SongsModel(id = 10, title = "Blinding Lights", album = "After Hours", singer = "The Weeknd", coverUri = "", url = ""),
            SongsModel(id = 11, title = "Heat Waves", album = "Dreamland", singer = "Glass Animals", coverUri = "", url = ""),
            SongsModel(id = 12, title = "Solar Power", album = "Solar Power", singer = "Lorde", coverUri = "", url = ""),
        )

        // Query with artist first, title second
        val reranked1 = SmartMusicRanker.rerankSearchResults(context, songs, "glass animals heat waves")
        assertEquals(11, reranked1.first().id)

        // Query with title first, artist second
        val reranked2 = SmartMusicRanker.rerankSearchResults(context, songs, "heat waves glass animals")
        assertEquals(11, reranked2.first().id)
    }

    @Test
    fun verifyPunctuationStrippedMatching() {
        val songs = listOf(
            SongsModel(id = 20, title = "Don't Start Now", album = "Future Nostalgia", singer = "Dua Lipa", coverUri = "", url = ""),
            SongsModel(id = 21, title = "Levitating", album = "Future Nostalgia", singer = "Dua Lipa", coverUri = "", url = ""),
            SongsModel(id = 22, title = "Physical", album = "Future Nostalgia", singer = "Dua Lipa", coverUri = "", url = ""),
        )

        // User typed without apostrophe
        val reranked = SmartMusicRanker.rerankSearchResults(context, songs, "dont start now")
        assertEquals(20, reranked.first().id)
        assertEquals("Don't Start Now", reranked.first().title)
    }

    @Test
    fun verifyUnspacedQueryMatchesSpacedTitle() {
        val songs = listOf(
            SongsModel(id = 30, title = "Heat Waves", album = "Dreamland", singer = "Glass Animals", coverUri = "", url = ""),
            SongsModel(id = 31, title = "Cold Water", album = "Single", singer = "Major Lazer", coverUri = "", url = ""),
        )

        // User typed compound word "heatwaves"
        val reranked = SmartMusicRanker.rerankSearchResults(context, songs, "heatwaves")
        assertEquals(30, reranked.first().id)
    }
}
