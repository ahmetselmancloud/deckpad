package com.dokunmatikekosistem.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Faz 1 scaffold: no settings are read or written yet.
 * Faz 5 (Ayarlar Ekranı) will add real keys and read/write functions here.
 */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
