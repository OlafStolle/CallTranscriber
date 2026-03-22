package com.calltranscriber.di

import android.content.Context
import androidx.room.Room
import com.calltranscriber.data.local.CallDatabase
import com.calltranscriber.data.local.CallDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCallDatabase(@ApplicationContext context: Context): CallDatabase {
        return Room.databaseBuilder(context, CallDatabase::class.java, "call_transcriber.db").build()
    }

    @Provides
    fun provideCallDao(db: CallDatabase): CallDao = db.callDao()
}
