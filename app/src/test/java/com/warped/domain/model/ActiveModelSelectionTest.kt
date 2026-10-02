package com.warped.domain.model

import com.google.common.truth.Truth.assertThat
import com.warped.data.local.security.KeystoreManager
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Quick-task lazy-model-load: selecting a model only marks it pending
 * ([ActiveModelSelection.selectLocalPending] — selected, not loading);
 * the engine mounts on the first send. `markLocalLoading` is the load
 * START signal only (send path), cleared by `connectLocal` /
 * `markLocalDisconnected`, and never rehydrated as stuck-loading.
 */
class ActiveModelSelectionTest {

    private fun keystore(persistedLocalJson: String? = null): KeystoreManager {
        val ks = mockk<KeystoreManager>()
        every { ks.get(any()) } returns null
        if (persistedLocalJson != null) {
            every { ks.get("last_active_local") } returns persistedLocalJson
        }
        every { ks.put(any(), any()) } just Runs
        every { ks.remove(any()) } just Runs
        return ks
    }

    @Test
    fun `selectLocalPending marks selected without loading`() {
        val selection = ActiveModelSelection(keystore())

        selection.selectLocalPending("/models/a.litertlm")

        val local = selection.localSelection.value
        assertThat(local.modelId).isEqualTo("/models/a.litertlm")
        assertThat(local.isConnected).isFalse()
        assertThat(local.isLoading).isFalse()
    }

    @Test
    fun `markLocalLoading signals load start`() {
        val selection = ActiveModelSelection(keystore())

        selection.selectLocalPending("/models/a.litertlm")
        selection.markLocalLoading("/models/a.litertlm")

        val local = selection.localSelection.value
        assertThat(local.modelId).isEqualTo("/models/a.litertlm")
        assertThat(local.isConnected).isFalse()
        assertThat(local.isLoading).isTrue()
    }

    @Test
    fun `connectLocal clears loading`() {
        val selection = ActiveModelSelection(keystore())

        selection.markLocalLoading("/models/a.litertlm")
        selection.connectLocal("/models/a.litertlm", ProviderType.LITE_RT_LM)

        val local = selection.localSelection.value
        assertThat(local.isConnected).isTrue()
        assertThat(local.isLoading).isFalse()
    }

    @Test
    fun `markLocalDisconnected keeps selection for retry and clears loading`() {
        val selection = ActiveModelSelection(keystore())

        selection.markLocalLoading("/models/a.litertlm")
        selection.markLocalDisconnected()

        val local = selection.localSelection.value
        assertThat(local.modelId).isEqualTo("/models/a.litertlm")
        assertThat(local.isConnected).isFalse()
        assertThat(local.isLoading).isFalse()
    }

    @Test
    fun `restore rehydrates pending never stuck loading`() {
        val selection = ActiveModelSelection(
            keystore(persistedLocalJson = """{"modelId":"/models/a.litertlm"}""")
        )

        val local = selection.localSelection.value
        assertThat(local.modelId).isEqualTo("/models/a.litertlm")
        assertThat(local.isConnected).isFalse()
        assertThat(local.isLoading).isFalse()
    }

    @Test
    fun `pending local does not claim the active model`() {
        val selection = ActiveModelSelection(keystore())

        selection.selectLocalPending("/models/a.litertlm")

        // Pending ≠ serving: no active model until the engine connects.
        assertThat(selection.activeModel.value).isNull()
    }

    @Test
    fun `connected local claims the active model`() {
        val selection = ActiveModelSelection(keystore())

        selection.connectLocal("/models/a.litertlm", ProviderType.LITE_RT_LM)

        assertThat(selection.activeModel.value?.modelId).isEqualTo("/models/a.litertlm")
        assertThat(selection.activeModel.value?.providerType).isEqualTo(ProviderType.LITE_RT_LM)
    }
}
