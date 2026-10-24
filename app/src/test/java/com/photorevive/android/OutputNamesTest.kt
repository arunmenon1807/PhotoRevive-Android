package com.photorevive.android
import org.junit.Assert.*
import org.junit.Test
class OutputNamesTest {
 @Test fun pathSeparatorsCannotEscapeAlbum() { assertEquals("A_B_C",OutputNames.album(" A/B\\C "));assertEquals("Restored",OutputNames.album("..")) }
 @Test fun emptyAndLongNamesAreSafe() { assertEquals("Restored",OutputNames.album(" "));assertEquals(80,OutputNames.album("x".repeat(100)).length) }
}
