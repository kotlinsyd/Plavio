package com.pulsestream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupReaderTest {
    @Test fun acceptsExactlyTheByteLimit() {
        assertEquals("1234", BackupManager.readBounded("1234".byteInputStream(), 4))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsOneByteOverTheLimit() {
        BackupManager.readBounded("12345".byteInputStream(), 4)
    }
    @Test fun preservesUnicodeWithoutConfusingBytesAndCharacters() {
        val text = "مرحبا"
        assertEquals(text, BackupManager.readBounded(text.byteInputStream(), text.toByteArray().size))
    }
}
