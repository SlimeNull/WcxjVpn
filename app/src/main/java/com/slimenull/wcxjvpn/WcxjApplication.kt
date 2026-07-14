package com.slimenull.wcxjvpn

import android.app.Application
import android.util.Log

class WcxjApplication : Application() {
    companion object {
        init {
            runCatching { System.loadLibrary("gadget") }
                .onFailure { Log.w("WCXJ", "Native bootstrap library was not loaded", it) }
        }
    }
}
