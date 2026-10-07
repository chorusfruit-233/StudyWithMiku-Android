package com.shshouse.studywithmiku

import android.app.Application

class MikuApplication : Application() {
    lateinit var store: MikuStore
        private set
    lateinit var timer: FocusEngine
        private set
    lateinit var onlineMusic: OnlineMusic
        private set
    override fun onCreate() {
        super.onCreate()
        store = MikuStore(this)
        timer = FocusEngine(this,store)
        onlineMusic = OnlineMusic(this,store)
    }
}
