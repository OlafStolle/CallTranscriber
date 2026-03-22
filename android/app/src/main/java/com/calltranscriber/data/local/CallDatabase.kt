package com.calltranscriber.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CallEntity::class], version = 1)
abstract class CallDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
}
