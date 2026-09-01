package dev.andrej.echo.data

class FakeSettingsStore(initial: String? = null) : SettingsStore {
    override var languageTag: String? = initial
}
