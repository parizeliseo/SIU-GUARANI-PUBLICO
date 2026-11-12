package ar.pariz.guarani

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        // Nada de esto puede impedir que la app abra.
        runCatching { Profiles.all(this).forEach { Store.ensureSeed(this, it.id) } }
        runCatching { Scheduler.schedule(this, replace = false) }
        runCatching { Scheduler.keepAlive(this) }
        runCatching { WatchService.sync(this) }
    }
}
