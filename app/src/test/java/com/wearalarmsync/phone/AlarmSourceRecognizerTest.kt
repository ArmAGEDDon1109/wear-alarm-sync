package com.wearalarmsync.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSourceRecognizerTest {

    @Test
    fun `empty allowed set never allows, even with known creator`() {
        assertFalse(AlarmSourceRecognizer.isAllowedForCreator("com.google.android.deskclock", emptySet()))
    }

    @Test
    fun `known creator in allowed set is allowed`() {
        val allowed = setOf("com.google.android.deskclock", "com.android.deskclock")
        assertTrue(AlarmSourceRecognizer.isAllowedForCreator("com.google.android.deskclock", allowed))
    }

    @Test
    fun `known creator not in allowed set is denied`() {
        val allowed = setOf("com.google.android.deskclock")
        assertFalse(AlarmSourceRecognizer.isAllowedForCreator("com.some.other.app", allowed))
    }

    @Test
    fun `unknown creator (API less than 31 or missing showIntent) fails open and is allowed`() {
        // Регрессия: до фикса null-creator трактовался как "запрещено", из-за чего на Android 8-11
        // (creatorPackage недоступен ниже API 31) синхронизация будильника не работала никогда.
        val allowed = setOf("com.google.android.deskclock")
        assertTrue(AlarmSourceRecognizer.isAllowedForCreator(null, allowed))
    }

    @Test
    fun `unknown creator with empty allowed set is still denied`() {
        assertFalse(AlarmSourceRecognizer.isAllowedForCreator(null, emptySet()))
    }
}
