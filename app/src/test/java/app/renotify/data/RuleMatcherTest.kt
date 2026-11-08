package app.renotify.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
    fun nullOrEmptySenderFilterListMatchesAnyNotificationTitle() {
        assertTrue(RuleMatcher.matchesSender(AppRule("com.instagram.android", senderFilters = emptyList()), "Anything"))
    }

    @Test
    fun exactMatchWithMultipleSpacesAndCaseVariations() {
        val rule = AppRule("com.instagram.android", senderFilters = listOf("Nok"), exactSenderMatch = true)
        assertTrue(RuleMatcher.matchesSender(rule, "  Nok  "))
        assertTrue(RuleMatcher.matchesSender(rule, "nok"))
        assertTrue(RuleMatcher.matchesSender(rule, "NOK"))
        // Title is trimmed before comparison, so trailing spaces don't matter
        assertTrue(RuleMatcher.matchesSender(rule, "Nok "))
        assertTrue(RuleMatcher.matchesSender(rule, " nok "))
    }

    @Test
    fun partialMatchIsCaseInsensitiveAndSubstringBased() {
        val rule = AppRule("com.instagram.android", senderFilters = listOf("msg"), exactSenderMatch = false)
        assertTrue(RuleMatcher.matchesSender(rule, "New msg from Instagram"))
        assertTrue(RuleMatcher.matchesSender(rule, "MSG notification"))
        assertFalse(RuleMatcher.matchesSender(rule, "Hello world"))
    }

    @Test
    fun senderFilterWithOnlyBlankStringsMatchesEveryTitle() {
        assertTrue(RuleMatcher.matchesSender(AppRule("com.instagram.android", senderFilters = listOf(" ", "  ", "")), "Someone"))
    }

    @Test
    fun senderFilterMixedBlankAndRealFiltersIgnoresBlanks() {
        val rule = AppRule("com.instagram.android", senderFilters = listOf(" ", "Nok", ""), exactSenderMatch = true)
        assertTrue(RuleMatcher.matchesSender(rule, "Nok"))
        assertFalse(RuleMatcher.matchesSender(rule, "Mali"))
    }

    @Test
    fun unlimitedRepeatPolicyNeverReachesALimit() {
        assertTrue(ReminderPolicy.shouldRepeat(0, null))
        assertTrue(ReminderPolicy.shouldRepeat(100_000, null))
        assertFalse(ReminderPolicy.shouldRepeat(3, 3))
    }

    @Test
    fun repeatPolicyWithZeroMaxRepeatsNeverRepeats() {
        assertFalse(ReminderPolicy.shouldRepeat(0, 0))
        assertFalse(ReminderPolicy.shouldRepeat(5, 0))
    }

    @Test
    fun repeatPolicyAllowsRepeatsUntilMaxReached() {
        assertTrue(ReminderPolicy.shouldRepeat(0, 3))
        assertTrue(ReminderPolicy.shouldRepeat(1, 3))
        assertTrue(ReminderPolicy.shouldRepeat(2, 3))
        assertFalse(ReminderPolicy.shouldRepeat(3, 3))
    }

    @Test
    fun repeatPolicyWithMaxRepeatsOfOneAllowsSingleRepeat() {
        assertTrue(ReminderPolicy.shouldRepeat(0, 1))
        assertFalse(ReminderPolicy.shouldRepeat(1, 1))
    }

    @Test
    fun failedReminderDeliveryIsRetriedAfterFifteenMinutes() {
        assertEquals(901_000L, ReminderPolicy.retryAt(1_000L))
    }

    @Test
    fun retryDelayIsConstantRegardlessOfInput() {
        assertEquals(900_000L, ReminderPolicy.retryAt(0L))
        assertEquals(901_000L, ReminderPolicy.retryAt(1_000L))
        assertEquals(1_000_900_000L, ReminderPolicy.retryAt(1_000_000_000L))
    }
}
