package br.com.selebi.app.nucleo.dados

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** O mesmo arquivo de antes (`preferencias`): quem atualiza o app não perde a empresa nem o CPF. */
val Context.preferenciasApp: DataStore<Preferences> by preferencesDataStore(name = "preferencias")
