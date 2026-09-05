package com.example.brain

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.brain.data.AppDatabase
import com.example.brain.data.repository.AppRepository
import com.example.brain.util.BackupManager
import com.example.brain.util.SecurityManager
import com.example.brain.worker.ReminderWorker
import java.util.concurrent.TimeUnit

class BrainApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: AppRepository
        private set

    lateinit var securityManager: SecurityManager
        private set

    lateinit var backupManager: BackupManager
        private set

    lateinit var vaultCryptoManager: com.example.brain.util.VaultCryptoManager
        private set

    lateinit var biometricAuthManager: com.example.brain.util.BiometricAuthManager
        private set

    lateinit var backupRecoveryManager: com.example.brain.util.BackupRecoveryManager
        private set

    lateinit var dataIntegrityManager: com.example.brain.util.DataIntegrityManager
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Database & Repository
        database = AppDatabase.getInstance(this)
        repository = AppRepository(database)

        // 2. Initialize Security & Backup Managers
        securityManager = SecurityManager(this)
        backupManager = BackupManager(this, database)
        vaultCryptoManager = com.example.brain.util.VaultCryptoManager(this)
        biometricAuthManager = com.example.brain.util.BiometricAuthManager(this)
        backupRecoveryManager = com.example.brain.util.BackupRecoveryManager(this, database, vaultCryptoManager)
        dataIntegrityManager = com.example.brain.util.DataIntegrityManager(this, database, vaultCryptoManager)

        // 3. Schedule Spaced Repetition Resurfacing Worker
        scheduleResurfacingWorker()
    }

    private fun scheduleResurfacingWorker() {
        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "second_brain_resurface_work",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
