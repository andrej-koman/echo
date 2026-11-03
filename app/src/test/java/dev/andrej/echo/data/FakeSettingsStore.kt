package dev.andrej.echo.data

class FakeSettingsStore(initial: String? = null) : SettingsStore {
    override var transcriptionLanguage: String? = initial
    override var autoAnalyze: Boolean = true
    override var detectSpeakers: Boolean = true
    override var wifiOnlyDownload: Boolean = false
    override var onboardingDone: Boolean = false
    override var speechModel: String? = null
}
