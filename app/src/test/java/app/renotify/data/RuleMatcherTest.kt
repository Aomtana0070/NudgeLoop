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
    fun exactMatchIgnoresCaseAndOuterSpacesForAnySelectedSender() {
        val rule = AppRule("com.instagram.android", senderFilters = listOf("Nok", "Pimchaka"))
        assertTrue(RuleMatcher.matchesSender(rule, " nok "))
        assertTrue(RuleMatcher.matchesSender(rule, "pimchaka"))
        assertFalse(RuleMatcher.matchesSender(rule, "Nok sent a message"))
        assertFalse(RuleMatcher.matchesSender(rule, "Mali"))
    }

    @Test
    fun partialMatchCanBeSelectedExplicitly() {
        val rule = AppRule("com.instagram.android", senderFilters = listOf("Nok", "Pimc"), exactSenderMatch = false)
        assertTrue(RuleMatcher.matchesSender(rule, "Pimchaka sent a message"))
        assertFalse(RuleMatcher.matchesSender(rule, "Mali sent a message"))
    }

    @Test
    fun blankSenderFiltersMatchEveryNotificationTitle() {
        assertTrue(RuleMatcher.matchesSender(AppRule("com.instagram.android", senderFilters = listOf(" ")), "Someone"))
    }

    @Test
    fun unlimitedRepeatPolicyNeverReachesALimit() {
        assertTrue(ReminderPolicy.shouldRepeat(0, null))
        assertTrue(ReminderPolicy.shouldRepeat(100_000, null))
        assertFalse(ReminderPolicy.shouldRepeat(3, 3))
    }
}
