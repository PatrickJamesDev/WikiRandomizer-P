package org.nsh07.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // The application id for the running build variant is read from the instrumentation arguments.
        rule.collect(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw Exception("targetAppId not passed as instrumentation runner arg"),

            // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("Featured article")), 5000)

            // Article reading is the main user journey: open a real article, scroll through its
            // sections, expand one, and open the lead picture. This brings the parser, article
            // list, section animation, Coil and the gallery into the baseline profile instead of
            // profiling only the feed/settings chrome.
            openArticle("Photosynthesis", device)

            device.findObject(By.descContains("language")).click()
            Thread.sleep(1000)
            device.pressBack()

            navigateTo("Saved", device)
            navigateTo("History", device)
            navigateTo("Settings", device)
            navigateTo("About", device)
        }
    }

    private fun openArticle(title: String, device: UiDevice) {
        val search = device.wait(Until.findObject(By.text("Search Wikipedia")), 5000) ?: return
        search.click()
        val field = device.wait(Until.findObject(By.focused(true)), 3000) ?: return
        field.text = title
        device.pressEnter()
        // Section headers only exist inside the article, so this also proves the parser finished.
        if (!device.wait(Until.hasObject(By.descContains("Expand section")), 15_000)) return
        Thread.sleep(1500)

        val width = device.displayWidth
        val height = device.displayHeight
        repeat(3) {
            device.swipe(width / 2, (height * 0.78f).toInt(), width / 2, (height * 0.25f).toInt(), 24)
            Thread.sleep(700)
        }

        device.findObject(By.descContains("Expand section"))?.click()
        Thread.sleep(1200)
        device.swipe(width / 2, (height * 0.75f).toInt(), width / 2, (height * 0.3f).toInt(), 24)
        Thread.sleep(700)

        // The lead picture's semantics description is the article title; opening and closing it
        // warms the full-screen image path too.
        device.findObject(By.desc(title))?.click()
        Thread.sleep(1200)
        device.pressBack()
        Thread.sleep(800)
        device.pressBack()
        Thread.sleep(800)
    }

    fun navigateTo(text: String, device: UiDevice) {
        device.findObject(By.desc("More options")).click()
        Thread.sleep(1000)

        device.findObject(By.text(text)).click()
        Thread.sleep(1000)
        device.findObject(By.desc("Go back")).click()
        Thread.sleep(1000)
    }
}