package com.kuromify.kuromix

import android.app.Application
import com.topjohnwu.superuser.Shell

class KuroMix : Application() {

    override fun onCreate() {
        super.onCreate()
        // Configure libsu once, globally, before any Shell.getShell() call.
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
        )
    }
}
