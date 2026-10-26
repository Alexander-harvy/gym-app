package com.mygym.log

import android.app.Application
import androidx.room.Room
import com.mygym.log.data.*

class GymApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, GymDatabase::class.java, "gym.db").addMigrations(GymDatabase.MIGRATION_1_2).build() }
    val repository by lazy { GymRepository(database, PreferenceStore(this)) }
    val backup by lazy { BackupService(repository, contentResolver) }
}
