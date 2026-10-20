package io.github.chorusfruit233.studywithmiku

import android.graphics.Bitmap
import android.graphics.drawable.Animatable
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ImageLoadingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun productionImageLoaderFetchesAndDecodesNetworkCover() = runBlocking {
        val bitmap = Bitmap.createBitmap(3,2,Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG,100,output)
            output.toByteArray()
        }
        bitmap.recycle()
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type","image/png").setBody(Buffer().write(bytes)))
            server.start()
            val result = SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context).data(server.url("/cover.png").toString()).build())
            assertTrue("Network image failed: $result",result is SuccessResult)
            assertEquals(3,(result as SuccessResult).image.width)
            assertEquals(2,result.image.height)
            assertEquals("/cover.png",server.takeRequest(2,TimeUnit.SECONDS)?.path)
        } finally { server.shutdown() }
    }

    @Test fun productionImageLoaderKeepsBundledStickersAnimated() = runBlocking {
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context).data("file:///android_asset/sticker/12.gif").build())
        assertTrue("GIF sticker failed: $result",result is SuccessResult)
        val image = (result as SuccessResult).image
        assertTrue(image.width > 0 && image.height > 0)
        assertTrue("Animated sticker became a still image",image.asDrawable(context.resources) is Animatable)
    }
}
