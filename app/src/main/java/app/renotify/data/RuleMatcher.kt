package app.renotify.data

object RuleMatcher {
    fun matchesSender(rule: AppRule, notificationTitle: String): Boolean {
        val filter = rule.senderFilter.trim()
        if (filter.isEmpty()) return true
        return if (rule.exactSenderMatch) {
            notificationTitle.trim().equals(filter, ignoreCase = true)
        } else {
            notificationTitle.contains(filter, ignoreCase = true)
        }
    }
}
