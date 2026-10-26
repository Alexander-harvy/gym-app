package com.mygym.log.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import com.mygym.log.domain.Rules

private val Context.gymPreferences by preferencesDataStore("settings")
class PreferenceStore(context: Context) {
    private val store = context.gymPreferences
    private val unit = stringPreferencesKey("unit")
    private val theme = stringPreferencesKey("theme")
    private val rest = intPreferencesKey("rest")
    private val auto = booleanPreferencesKey("auto")
    private val seeded = booleanPreferencesKey("seeded")
    val flow = store.data.map { Preferences(it[unit] ?: "kg", it[theme] ?: "DARK", it[rest] ?: 90, it[auto] ?: true) }
    val isSeeded = store.data.map { it[seeded] ?: false }
    suspend fun markSeeded() { store.edit { it[seeded] = true } }
    suspend fun save(p: Preferences) {
        Rules.preferences(p)
        store.edit { it[unit] = p.unit; it[theme] = p.theme; it[rest] = p.restSeconds; it[auto] = p.autoRest }
    }
}
