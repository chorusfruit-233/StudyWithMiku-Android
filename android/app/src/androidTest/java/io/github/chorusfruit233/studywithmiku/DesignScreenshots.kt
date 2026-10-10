package io.github.chorusfruit233.studywithmiku

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** The shell owns these offline fixture images so test APK cleanup does not erase them. */
internal fun saveDesignScreenshot(name: String) {
    require(name.matches(Regex("[a-z-]+")))
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val directory = "/sdcard/Download/studywithmiku-design"
    fun command(value: String): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(value)).use { it.readBytes() }
    // UiAutomation executes arguments directly; it does not interpret shell operators.
    command("mkdir -p $directory")
    command("screencap -p $directory/$name.png")
    val bytes = command("cat $directory/$name.png")
    val signature = byteArrayOf(-119,80,78,71,13,10,26,10)
    check(bytes.size > signature.size && bytes.take(signature.size).toByteArray().contentEquals(signature)) {
        "Screenshot export did not produce a PNG: $name"
    }
}
