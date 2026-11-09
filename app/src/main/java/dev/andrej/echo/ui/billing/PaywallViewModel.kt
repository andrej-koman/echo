package dev.andrej.echo.ui.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.andrej.echo.billing.Billing
import dev.andrej.echo.billing.BillingPurchaseCancelledException
import dev.andrej.echo.billing.BillingUnavailableException
import dev.andrej.echo.billing.Plan
import dev.andrej.echo.billing.PlanPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallState(
    val plans: List<Plan> = emptyList(),
    val selected: Plan? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

class PaywallViewModel(private val billing: Billing) : ViewModel() {
    private val mutableState = MutableStateFlow(PaywallState())
    val state = mutableState.asStateFlow()
    val isPro = billing.isPro
    val activePlan = billing.activePlan

    fun load() {
        if (state.value.loading || state.value.busy) return
        mutableState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            try {
                val plans = billing.offerings()
                mutableState.update { current ->
                    current.copy(
                        plans = plans,
                        selected = plans.firstOrNull { it.id == current.selected?.id }
                            ?: plans.firstOrNull { it.period == PlanPeriod.Yearly } ?: plans.firstOrNull(),
                        message = if (plans.isEmpty()) "Purchases are unavailable right now." else null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(message = errorMessage(e)) }
            } finally {
                mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun select(plan: Plan) {
        if (!state.value.busy && plan in state.value.plans) mutableState.update { it.copy(selected = plan) }
    }

    fun purchase(activity: Activity) {
        val selected = state.value.selected ?: return
        perform { billing.purchase(activity, selected) }
    }

    fun restore() = perform {
        billing.restore()
        mutableState.update { it.copy(message = if (billing.isPro.value) "Purchases restored." else "No Pro purchases found for this Google Play account.") }
    }

    private fun perform(action: suspend () -> Unit) {
        if (state.value.busy || state.value.loading) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (e: BillingPurchaseCancelledException) {
                // Closing Play's purchase sheet is a normal dismissal.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(message = errorMessage(e)) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun errorMessage(error: Exception): String = when (error) {
        is BillingUnavailableException -> "Purchases are unavailable in this build."
        else -> "Connect to the internet to purchase or restore, then try again."
    }
}
