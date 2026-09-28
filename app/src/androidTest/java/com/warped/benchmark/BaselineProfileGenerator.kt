package com.warped.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile generator (PERF-16).
 *
 * Drives the app's critical user journey so ART pre-compiles the startup +
 * chat path into the release Baseline Profile:
 *
 * 1. Cold start (`pressHome` + `startActivityAndWait`, `includeInStartupProfile = true`)
 * 2. Fresh-install wizard dismissal ("Skip all" -> confirm "Skip" in the "Skip wizard" dialog;
 *    no-op on later runs where the wizard is already complete)
 * 3. Wait for the chat screen (model-picker chevron, contentDescription "Select model")
 * 4. Scroll the chat list (no-op on an empty conversation — the scrollable is absent then)
 * 5. Open the model picker ("Select model") and return (covers the selector destination)
 *
 * Generate on hardware with:
 *   ./gradlew :app:generateReleaseBaselineProfile
 * then copy the emitted profile to `app/src/main/baselineProfiles/baseline-prof.txt`
 * (find it with: `find app/src -name baseline-prof.txt -path "*generated*"`).
 * The checked-in `baseline-prof.txt` is a hand-seeded startup-path seed until this
 * generator is run on a rooted/userdebug device or CI (see BENCHMARKS.md section 1).
 *
 * Requires a device that allows compilation control (rooted/userdebug); on user
 * builds profile collection cannot reset ART state and the task fails — record
 * numbers on CI (Pixel 7 reference) instead.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(
            packageName = "com.warped.app",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            dismissWizardIfPresent()

            // Critical journey: chat list visible.
            device.wait(Until.hasObject(By.desc("Select model")), 5_000)

            // Scroll the chat list when it is scrollable (non-empty conversation).
            val scrollable = UiScrollable(UiSelector().scrollable(true))
            if (scrollable.exists()) {
                repeat(3) { scrollable.flingForward() }
                repeat(3) { scrollable.flingBackward() }
            }

            // Open the model picker, let it settle, return to chat.
            device.findObject(By.desc("Select model"))?.click()
            device.waitForIdle()
            device.pressBack()
            device.wait(Until.hasObject(By.desc("Select model")), 5_000)
        }
    }

    /**
     * Fresh installs land on the onboarding wizard (NavGraph navigates to
     * Screen.Wizard while `isWizardComplete == false`). Dismiss it via the
     * "Skip all" TopAppBar action + the "Skip" confirm button so the profile
     * covers the real post-wizard startup path. No-op when the wizard is
     * already complete (texts absent).
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.dismissWizardIfPresent() {
        val skipAll = device.findObject(By.text("Skip all"))
        if (skipAll != null) {
            skipAll.click()
            device.wait(Until.hasObject(By.text("Skip wizard")), 5_000)
            device.findObject(By.text("Skip"))?.click()
        }
    }
}
