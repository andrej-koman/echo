package dev.andrej.echo.data

class FakeSettingsStore(initial: String? = null) : SettingsStore {
    override var languageTag: String? = initial
    override var autoAnalyze: Boolean = true
    override var wifiOnlyDownload: Boolean = false
    override var reminderLeadMinutes: Int = 15
    override var reminderMorningHour: Int = 9
    override var reminderMorningMinute: Int = 0
    override var preferredLlmBackend: String? = null
    override var snoozeMinutes: Int = 60
}
