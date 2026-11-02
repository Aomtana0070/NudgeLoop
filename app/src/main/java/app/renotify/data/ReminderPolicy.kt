package app.renotify.data

object ReminderPolicy {
    fun shouldRepeat(sentRepeats: Int, maxRepeats: Int?): Boolean =
        maxRepeats == null || sentRepeats < maxRepeats
}
