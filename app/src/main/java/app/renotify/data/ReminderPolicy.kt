package app.renotify.data

object ReminderPolicy {
    const val RETRY_DELAY_MILLIS = 15 * 60_000L

    fun shouldRepeat(sentRepeats: Int, maxRepeats: Int?): Boolean =
        maxRepeats == null || sentRepeats < maxRepeats

    fun retryAt(nowMillis: Long): Long = nowMillis + RETRY_DELAY_MILLIS
}
