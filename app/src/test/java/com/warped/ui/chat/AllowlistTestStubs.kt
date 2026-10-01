package com.warped.ui.chat

import com.warped.data.repository.ModelAllowlistRepository
import com.warped.domain.model.LocalModel
import io.mockk.every

/**
 * Stubs `effectiveCapabilities` on a strict allowlist mock to reflect the
 * fake model's own capabilities. Chat-flow tests construct fake
 * [LocalModel]s (all-true lazy caps) and stub `findByModelFile` for
 * unrelated concerns — without this, [ChatViewModel.verifiedLocalCapabilities]
 * hits an unstubbed strict mock and every send flow breaks.
 *
 * The verified-only policy itself is covered at the repository level
 * ([ModelAllowlistTest]); these tests are about chat behavior, not policy.
 */
fun stubEffectiveCapabilities(allowlist: ModelAllowlistRepository) {
    every { allowlist.effectiveCapabilities(any()) } answers {
        firstArg<LocalModel>().capabilities
    }
}
