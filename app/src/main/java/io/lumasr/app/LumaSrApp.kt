package io.lumasr.app

import android.app.Application
import io.lumasr.app.batch.BatchNotifications

class LumaSrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BatchNotifications.ensureChannel(this)
    }
}
