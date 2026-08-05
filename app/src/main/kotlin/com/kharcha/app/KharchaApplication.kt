package com.kharcha.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application root for the Hilt component graph. Also supplies WorkManager's
 * [Configuration] so that [com.kharcha.app.ingest.IngestWorker] and
 * [com.kharcha.app.ingest.BackfillWorker] — both `@HiltWorker`s — get their
 * dependencies injected via [HiltWorkerFactory] instead of needing a no-arg constructor.
 */
@HiltAndroidApp
class KharchaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
