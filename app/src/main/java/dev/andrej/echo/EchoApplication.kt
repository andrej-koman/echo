package dev.andrej.echo

import android.app.Application

class EchoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        container.billing.initialize()
    }

    val container: AppContainer by lazy { AppContainer(this) }
}
