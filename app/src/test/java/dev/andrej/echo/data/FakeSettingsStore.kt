package dev.andrej.echo.data

class FakeSettingsStore(initial: String? = null) : SettingsStore {
    override var languageTag: String? = initial
    override var autoAnalyze: Boolean = true
    override var wifiOnlyDownload: Boolean = false
    override var snoozeMinutes: Int = 60
    override var onboardingDone: Boolean = false
}
