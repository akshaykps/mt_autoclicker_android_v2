package net.mtautoclicker.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

private val Context.macroDataStore by preferencesDataStore(name = "mt_macros")

class MacroRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("macros_json")

    val macros = context.macroDataStore.data.map { prefs ->
        decodeList(prefs[key].orEmpty())
    }

    suspend fun allMacros(): List<SavedMacro> = macros.first()

    suspend fun saveMacro(macro: SavedMacro): SavedMacro {
        context.macroDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).toMutableList()
            current.removeAll { it.id == macro.id }
            current.add(0, macro)
            prefs[key] = encodeList(current)
        }
        return macro
    }

    suspend fun saveNewMacro(
        name: String,
        steps: List<MacroStep>,
    ): SavedMacro {
        val duration = steps.sumOf { it.delayMs + it.durationMs }
        val macro = SavedMacro(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = Instant.now().toString(),
            steps = steps,
            metadata = MacroMetadata(
                durationMs = duration,
                actionCount = steps.size,
            ),
        )
        return saveMacro(macro)
    }

    suspend fun deleteMacro(id: String) {
        context.macroDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).filterNot { it.id == id }
            prefs[key] = encodeList(current)
        }
    }

    suspend fun deleteAllMacros() {
        context.macroDataStore.edit { prefs ->
            prefs[key] = encodeList(emptyList())
        }
    }

    suspend fun macroCount(): Int = allMacros().size

    /** Merges macros by id; returns how many were newly added. */
    suspend fun importMacros(incoming: List<SavedMacro>): Int {
        if (incoming.isEmpty()) return 0
        var added = 0
        context.macroDataStore.edit { prefs ->
            val current = decodeList(prefs[key].orEmpty()).toMutableList()
            val existingIds = current.map { it.id }.toSet()
            incoming.forEach { m ->
                if (m.id !in existingIds) {
                    current.add(0, m)
                    added++
                }
            }
            prefs[key] = encodeList(current)
        }
        return added
    }

    private fun decodeList(raw: String): List<SavedMacro> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<SavedMacro>>(raw) }.getOrDefault(emptyList())
    }

    private fun encodeList(items: List<SavedMacro>): String = json.encodeToString(items)
}
