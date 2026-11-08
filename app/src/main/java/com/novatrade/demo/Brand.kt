package com.novatrade.demo

/**
 * The app's display name, read once from res/values/strings.xml (app_name) at start-up.
 * Change the name there and every screen follows.
 */
object Brand {
    var name: String = "NovaTrade"
        internal set

    /** Lowercase wordmark shown on the PIN and sign-in screens. */
    val wordmark: String get() = name.lowercase()
}
