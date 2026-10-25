package com.skillverse.app

import android.app.Application
import com.skillverse.app.notifications.ReminderNotifications

class SkillVerseApplication : Application() {
    private var containerOverride: AppContainer? = null

    /** Lazily created; tests may replace it before any UI is created. */
    var container: AppContainer
        get() = containerOverride ?: DefaultAppContainer(this).also { containerOverride = it }
        set(value) { containerOverride = value }

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.ensureChannel(this)
    }
}
