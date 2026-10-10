package io.github.chorusfruit233.studywithmiku

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** The shell owns these offline fixture images so test APK cleanup does not erase them. */
internal fun saveDesignScreenshot(name: String) {
    require(name.matches(Regex("[a-z-]+")))
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val directory = "/sdcard/Download/studywithmiku-design"
    val output = automation.executeShellCommand("mkdir -p $directory && screencap -p $directory/$name.png")
    ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
}
