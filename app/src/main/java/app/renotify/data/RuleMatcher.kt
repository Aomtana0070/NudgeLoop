package app.renotify.data

object RuleMatcher {
    fun matchesSender(rule: AppRule, notificationTitle: String): Boolean {
        val filters = rule.senderFilters.map(String::trim).filter(String::isNotEmpty)
        if (filters.isEmpty()) return true
        return filters.any { filter ->
            if (rule.exactSenderMatch) {
                notificationTitle.trim().equals(filter, ignoreCase = true)
            } else {
                notificationTitle.contains(filter, ignoreCase = true)
            }
        }
    }

    fun bypassesQuietHours(rule: AppRule, notificationTitle: String): Boolean {
        val bypassSenders = rule.quietHoursBypassSenders.map(String::trim).filter(String::isNotEmpty)
        return bypassSenders.any { sender ->
            if (rule.exactSenderMatch) {
                notificationTitle.trim().equals(sender, ignoreCase = true)
            } else {
                notificationTitle.contains(sender, ignoreCase = true)
            }
        }
    }
}
