package app.renotify.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {
    @Test
    fun emptySenderFilterMatchesAnyNotificationTitle() {
        assertTrue(RuleMatcher.matchesSender(AppRule("com.instagram.android"), "Someone"))
    }

    @Test
    fun exactMatchIgnoresCaseAndOuterSpaces() {
        val rule = AppRule("com.instagram.android", senderFilter = "Nok")
        assertTrue(RuleMatcher.matchesSender(rule, " nok "))
        assertFalse(RuleMatcher.matchesSender(rule, "Nok sent a message"))
    }

    @Test
    fun partialMatchCanBeSelectedExplicitly() {
        val rule = AppRule("com.instagram.android", senderFilter = "Nok", exactSenderMatch = false)
        assertTrue(RuleMatcher.matchesSender(rule, "Nok sent a message"))
        assertFalse(RuleMatcher.matchesSender(rule, "Mali sent a message"))
    }
}
