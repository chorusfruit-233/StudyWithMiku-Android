package io.github.chorusfruit233.studywithmiku

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.gif.AnimatedImageDecoder

class MikuApplication : Application(), SingletonImageLoader.Factory {
    val audioCache by lazy { AudioCache(this,store) }
    lateinit var store: MikuStore
        private set
    lateinit var timer: FocusEngine
        private set
    lateinit var onlineMusic: OnlineMusic
        private set
    lateinit var auth: StudyAuth
        private set
    lateinit var sync: CloudSync
        private set
    lateinit var chat: ChatClient
        private set
    lateinit var extras: OnlineExtras
        private set
    override fun onCreate() {
        super.onCreate()
        store = MikuStore(this)
        timer = FocusEngine(this,store)
        onlineMusic = OnlineMusic(this,store)
        val api = StudyApi()
        auth = StudyAuth(this,api)
        chat = ChatClient(auth,api)
        sync = CloudSync(store,timer,auth,api)
        extras = OnlineExtras(this)
        sync.start()
    }
    override fun newImageLoader(context: Context) = ImageLoader.Builder(context).components {
        if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
    }.build()
}
