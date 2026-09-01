package dev.andrej.echo

import android.app.Application

class EchoApplication : Application() {

    /** Created lazily so tests that never touch it pay nothing. */
    val container: AppContainer by lazy { AppContainer(this) }
}
