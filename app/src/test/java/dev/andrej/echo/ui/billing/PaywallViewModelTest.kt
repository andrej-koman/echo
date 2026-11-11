package dev.andrej.echo.ui.billing

import dev.andrej.echo.billing.Billing
import dev.andrej.echo.billing.FakeBilling
import dev.andrej.echo.billing.Plan
import dev.andrej.echo.billing.PlanPeriod
import dev.andrej.echo.data.FakeSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun `offering selects yearly and keeps explicit selection on reload`() = runTest {
        val fake = FakeBilling(FakeSettingsStore())
        val monthly = Plan("monthly", PlanPeriod.Monthly, "€4.99")
        val yearly = Plan("yearly", PlanPeriod.Yearly, "€19.99")
        val billing = object : Billing by fake {
            override suspend fun offerings() = listOf(monthly, yearly)
        }
        val vm = PaywallViewModel(billing)
        vm.load()
        advanceUntilIdle()
        assertEquals(yearly, vm.state.value.selected)
        vm.select(monthly)
        vm.load()
        advanceUntilIdle()
        assertEquals(monthly, vm.state.value.selected)
    }

    @Test fun `unconfigured offering explains availability without granting pro`() = runTest {
        val fake = FakeBilling(FakeSettingsStore())
        val vm = PaywallViewModel(fake)
        vm.load()
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.message!!.contains("unavailable"))
        assertFalse(vm.isPro.value)
    }

    @Test fun `failed restore preserves cached pro and exposes retryable error`() = runTest {
        val fake = FakeBilling(FakeSettingsStore().apply { cachedIsPro = true })
        val vm = PaywallViewModel(fake)
        vm.restore()
        advanceUntilIdle()
        assertTrue(vm.isPro.value)
        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.message!!.contains("unavailable"))
    }

    @Test fun `restore that activates pro is acknowledged once, not when already pro`() = runTest {
        val pro = kotlinx.coroutines.flow.MutableStateFlow(false)
        val fake = FakeBilling(FakeSettingsStore())
        val billing = object : Billing by fake {
            override val isPro = pro
            override suspend fun restore() { pro.value = true }
        }
        val vm = PaywallViewModel(billing)
        vm.restore()
        advanceUntilIdle()
        assertEquals(ProArrival.Restored, vm.state.value.arrival)
        vm.consumeArrival()
        vm.restore()
        advanceUntilIdle()
        assertNull(vm.state.value.arrival)
    }
}
