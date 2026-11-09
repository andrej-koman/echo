package dev.andrej.echo.billing

import android.app.Activity
import dev.andrej.echo.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlanPeriod { Monthly, Yearly, Lifetime }
data class Trial(val period: String, val cycles: Int)
data class Plan(
    val id: String,
    val period: PlanPeriod,
    val localizedPrice: String,
    val trial: Trial? = null,
    val localizedPricePerMonth: String? = null,
)

interface Billing {
    val isPro: StateFlow<Boolean>
    val activePlan: StateFlow<String?>
    val isConfigured: Boolean
    fun initialize()
    suspend fun offerings(): List<Plan>
    suspend fun purchase(activity: Activity, plan: Plan)
    suspend fun restore()
}

class BillingPurchaseCancelledException : Exception("Purchase cancelled")

class BillingUnavailableException : IllegalStateException("Purchases are unavailable in this build")

internal class EntitlementState(private val settings: SettingsStore) {
    private val pro = MutableStateFlow(settings.cachedIsPro)
    val isPro = pro.asStateFlow()
    private val plan = MutableStateFlow<String?>(null)
    val activePlan = plan.asStateFlow()

    @Synchronized fun update(isPro: Boolean, activePlan: String?) {
        settings.cachedIsPro = isPro
        plan.value = activePlan.takeIf { isPro }
        pro.value = isPro
    }
}

class FakeBilling(settings: SettingsStore) : Billing {
    private val state = EntitlementState(settings)
    override val isPro = state.isPro
    override val activePlan = state.activePlan
    override val isConfigured = false
    fun setPro(value: Boolean, activePlan: String? = null) = state.update(value, activePlan)
    override fun initialize() = Unit
    override suspend fun offerings(): List<Plan> = emptyList()
    override suspend fun purchase(activity: Activity, plan: Plan): Unit = throw BillingUnavailableException()
    override suspend fun restore(): Unit = throw BillingUnavailableException()
}
