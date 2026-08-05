package com.kharcha.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Placeholder application class so the manifest's android:name resolves and the
 * Hilt component graph has a root. Task 6 fleshes this out (SMS ingestion wiring,
 * WorkManager configuration, etc.) — extend this class there rather than replacing it.
 */
@HiltAndroidApp
class KharchaApplication : Application()
