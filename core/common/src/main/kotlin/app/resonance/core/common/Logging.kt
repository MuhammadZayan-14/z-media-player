package app.resonance.core.common

import timber.log.Timber

/**
 * Structured diagnostic logging.
 *
 * Messages are `event key=value` pairs so they can be grepped and parsed. Callers pass ids,
 * counts and error codes only — never titles, file paths or other user content, which keeps
 * personal data out of logcat and bug reports.
 */
object Log {
    fun d(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        Timber.tag(tag).d(format(event, fields))
    }

    fun i(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        Timber.tag(tag).i(format(event, fields))
    }

    fun w(tag: String, event: String, throwable: Throwable? = null, vararg fields: Pair<String, Any?>) {
        Timber.tag(tag).w(throwable, format(event, fields))
    }

    fun e(tag: String, event: String, throwable: Throwable? = null, vararg fields: Pair<String, Any?>) {
        Timber.tag(tag).e(throwable, format(event, fields))
    }

    internal fun format(event: String, fields: Array<out Pair<String, Any?>>): String =
        if (fields.isEmpty()) event else fields.joinToString(" ", prefix = "$event ") { (k, v) -> "$k=$v" }
}

/**
 * Release builds keep warnings and errors (without messages that could carry user data, which
 * [Log] already guarantees) so they can be forwarded to a crash reporter if one is added.
 */
class ReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= android.util.Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        android.util.Log.println(priority, tag ?: "Resonance", message)
    }
}
