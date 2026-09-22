package com.dokunmatikekosistem.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.dokunmatikekosistem.app.data.bluetooth.BluetoothHidManager
import com.dokunmatikekosistem.app.data.settings.settingsDataStore
import com.dokunmatikekosistem.app.domain.HidManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindHidManager(impl: BluetoothHidManager): HidManager

    companion object {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.settingsDataStore
    }
}
