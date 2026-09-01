package dev.andrej.echo

import android.app.Application

class EchoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
