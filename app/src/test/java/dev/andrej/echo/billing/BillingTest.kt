package dev.andrej.echo.billing

import dev.andrej.echo.data.FakeSettingsStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BillingTest {
    @Test fun `cached pro survives creation without network`() {
        val settings = FakeSettingsStore().apply { cachedIsPro = true }
        assertTrue(EntitlementState(settings).isPro.value)
        assertTrue(FakeBilling(settings).isPro.value)
    }

    @Test fun `successful entitlement refresh persists grants and revocations`() {
        val settings = FakeSettingsStore()
        val state = EntitlementState(settings)
        state.update(true, "echo_pro:yearly")
        assertTrue(settings.cachedIsPro)
        assertEquals("echo_pro:yearly", state.activePlan.value)
        state.update(false, null)
        assertFalse(state.isPro.value)
        assertFalse(settings.cachedIsPro)
        assertNull(state.activePlan.value)
    }

    @Test fun `fake can simulate entitlement changes for consumers`() {
        val billing = FakeBilling(FakeSettingsStore())
        billing.setPro(true, "yearly")
        assertTrue(billing.isPro.value)
        assertEquals("yearly", billing.activePlan.value)
        billing.setPro(false)
        assertFalse(billing.isPro.value)
    }

    @Test fun `unconfigured billing cannot grant purchases and restore preserves cache`() = runTest {
        val settings = FakeSettingsStore().apply { cachedIsPro = true }
        val billing = FakeBilling(settings)
        assertFalse(billing.isConfigured)
        assertTrue(billing.offerings().isEmpty())
        try {
            billing.restore()
            fail("Restore should be unavailable")
        } catch (_: BillingUnavailableException) {
            // Expected for builds without a billing key.
        }
        assertTrue(billing.isPro.value)
        assertTrue(settings.cachedIsPro)
    }
}
