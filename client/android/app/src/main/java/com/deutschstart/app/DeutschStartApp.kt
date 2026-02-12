package com.deutschstart.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DeutschStartApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize logging, etc.
    }
}
