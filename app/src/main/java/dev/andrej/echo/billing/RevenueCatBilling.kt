package dev.andrej.echo.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import dev.andrej.echo.data.SettingsStore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class RevenueCatBilling(
    private val context: Context,
    private val apiKey: String,
    settings: SettingsStore,
) : Billing {
    private val state = EntitlementState(settings)
    override val isPro = state.isPro
    override val activePlan = state.activePlan
    override val renewsAt = state.renewsAt
    override val isConfigured = true
    private var initialized = false
    private var packages: Map<String, Package> = emptyMap()

    override fun initialize() {
        if (initialized) return
        Purchases.configure(PurchasesConfiguration.Builder(context.applicationContext, apiKey).build())
        initialized = true
        Purchases.sharedInstance.updatedCustomerInfoListener = com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener {
            update(it)
        }
        Purchases.sharedInstance.getCustomerInfoWith(onError = {}, onSuccess = ::update)
    }

    private fun update(info: CustomerInfo) {
        val entitlement = info.entitlements["pro"]
        val active = entitlement?.isActive == true
        val plan = entitlement?.let {
            listOfNotNull(it.productIdentifier, it.productPlanIdentifier).joinToString(":")
        }
        val renews = entitlement?.takeIf { it.willRenew }?.expirationDate?.time
        state.update(active, plan, renews)
    }

    override suspend fun offerings(): List<Plan> = suspendCancellableCoroutine { continuation ->
        check(initialized)
        Purchases.sharedInstance.getOfferingsWith(
            onError = { continuation.resumeWithException(IllegalStateException(it.message)) },
            onSuccess = { offerings ->
                val available = (offerings.current ?: offerings.all["default"])?.availablePackages.orEmpty()
                packages = available.associateBy { it.identifier }
                continuation.resume(available.mapNotNull { item ->
                    val period = when (item.packageType) {
                        PackageType.MONTHLY -> PlanPeriod.Monthly
                        PackageType.ANNUAL -> PlanPeriod.Yearly
                        PackageType.LIFETIME -> PlanPeriod.Lifetime
                        else -> return@mapNotNull null
                    }
                    val product = item.product
                    val perMonth = if (period == PlanPeriod.Yearly) {
                        product.pricePerMonth()?.formatted
                    } else null
                    val trial = product.defaultOption?.freePhase?.let {
                        Trial(it.billingPeriod.iso8601, it.billingCycleCount ?: 1)
                    }
                    Plan(item.identifier, period, product.price.formatted, trial, perMonth, product.price.amountMicros, product.price.currencyCode)
                })
            },
        )
    }

    override suspend fun purchase(activity: Activity, plan: Plan): Unit = suspendCancellableCoroutine { continuation ->
        check(initialized)
        val item = requireNotNull(packages[plan.id]) { "Reload plans before purchasing" }
        Purchases.sharedInstance.purchaseWith(
            PurchaseParams.Builder(activity, item).build(),
            onError = { error, cancelled ->
                continuation.resumeWithException(if (cancelled) BillingPurchaseCancelledException() else IllegalStateException(error.message))
            },
            onSuccess = { _, info -> update(info); continuation.resume(Unit) },
        )
    }

    override suspend fun restore(): Unit = suspendCancellableCoroutine { continuation ->
        check(initialized)
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { continuation.resumeWithException(IllegalStateException(it.message)) },
            onSuccess = { update(it); continuation.resume(Unit) },
        )
    }
}
