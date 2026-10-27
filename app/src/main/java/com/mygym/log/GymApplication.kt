package com.mygym.log

import android.app.Application
import androidx.room.Room
import com.mygym.log.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class GymApplication : Application() {
    val applicationScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val runError=MutableStateFlow<String?>(null)
    val runCommandPending=MutableStateFlow(false)
    val database by lazy { Room.databaseBuilder(this, GymDatabase::class.java, "gym.db").addMigrations(GymDatabase.MIGRATION_1_2, GymDatabase.MIGRATION_2_3, GymDatabase.MIGRATION_3_4, GymDatabase.MIGRATION_4_5).build() }
    val repository by lazy { GymRepository(database, PreferenceStore(this)) }
    val backup by lazy { BackupService(repository, contentResolver) }
    val ready: Deferred<Unit> by lazy { applicationScope.async(start=CoroutineStart.LAZY) { RetiredMapCleanup.run(this@GymApplication); repository.runs.recoverInterrupted(); Unit } }
    override fun onCreate() { super.onCreate(); ready.start() }
}
