package com.creation.remoteman

import android.app.Application
import com.google.firebase.FirebaseApp

class RemoteManApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
