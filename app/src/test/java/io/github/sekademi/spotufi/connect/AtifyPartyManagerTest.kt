package io.github.sekademi.spotufi.connect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.sekademi.spotufi.connect.AtifyPartyManager.PartyRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AtifyPartyManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AtifyPartyManager.leaveOrEndParty()
    }

    @After
    fun tearDown() {
        AtifyPartyManager.leaveOrEndParty()
    }

    @Test
    fun verifyFormatPartyCodeHyphenatesCorrectly() {
        assertEquals("482-910", AtifyPartyManager.formatPartyCode("482910"))
        assertEquals("ABC-123", AtifyPartyManager.formatPartyCode("abc123"))
        assertEquals("123", AtifyPartyManager.formatPartyCode("123"))
    }

    @Test
    fun verifyCreatePartyGeneratesValidCodeAndSetsHostRole() {
        val formattedCode = AtifyPartyManager.createParty(context)

        assertEquals(PartyRole.HOST, AtifyPartyManager.partyRole.value)
        assertTrue(AtifyPartyManager.isHosting)
        assertFalse(AtifyPartyManager.isJoined)
        assertEquals(7, formattedCode.length) // "XXX-XXX"
        assertEquals('-', formattedCode[3])
        assertEquals(6, AtifyPartyManager.partyCode.value.length)
    }

    @Test
    fun verifyLeavePartyResetsAllState() {
        AtifyPartyManager.createParty(context)
        assertTrue(AtifyPartyManager.isHosting)

        AtifyPartyManager.leaveOrEndParty()

        assertEquals(PartyRole.NONE, AtifyPartyManager.partyRole.value)
        assertFalse(AtifyPartyManager.isHosting)
        assertFalse(AtifyPartyManager.isJoined)
        assertEquals("", AtifyPartyManager.partyCode.value)
        assertEquals("", AtifyPartyManager.formattedCode.value)
    }
}
