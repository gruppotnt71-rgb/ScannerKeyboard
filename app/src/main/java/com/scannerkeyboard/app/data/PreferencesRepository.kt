package com.scannerkeyboard.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class PostScanAction(val label: String) {
    NONE("Nessuna azione (solo testo)"),
    ENTER("Invia tasto INVIO (Enter)"),
    TAB("Invia tasto TAB (Campo successivo)"),
    EDITOR_ACTION("Esegui azione campo (Cerca / Vai / Fine)")
}

data class DataModifierRule(
    val pattern: String,
    val replacement: String
)

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("scanner_keyboard_prefs", Context.MODE_PRIVATE)

    var prefix: String
        get() = prefs.getString("prefix", "") ?: ""
        set(value) = prefs.edit().putString("prefix", value).apply()

    var suffix: String
        get() = prefs.getString("suffix", "") ?: ""
        set(value) = prefs.edit().putString("suffix", value).apply()

    var postScanAction: PostScanAction
        get() {
            val name = prefs.getString("post_scan_action", PostScanAction.ENTER.name)
            return try {
                PostScanAction.valueOf(name ?: PostScanAction.ENTER.name)
            } catch (_: Exception) {
                PostScanAction.ENTER
            }
        }
        set(value) = prefs.edit().putString("post_scan_action", value.name).apply()

    var beepOnScan: Boolean
        get() = prefs.getBoolean("beep_on_scan", true)
        set(value) = prefs.edit().putBoolean("beep_on_scan", value).apply()

    var vibrateOnScan: Boolean
        get() = prefs.getBoolean("vibrate_on_scan", true)
        set(value) = prefs.edit().putBoolean("vibrate_on_scan", value).apply()

    var torchAtStart: Boolean
        get() = prefs.getBoolean("torch_at_start", false)
        set(value) = prefs.edit().putBoolean("torch_at_start", value).apply()

    var continuousScan: Boolean
        get() = prefs.getBoolean("continuous_scan", false)
        set(value) = prefs.edit().putBoolean("continuous_scan", value).apply()

    var autoScanOnFocus: Boolean
        get() = prefs.getBoolean("auto_scan_on_focus", false)
        set(value) = prefs.edit().putBoolean("auto_scan_on_focus", value).apply()

    var showNumberRow: Boolean
        get() = prefs.getBoolean("show_number_row", true)
        set(value) = prefs.edit().putBoolean("show_number_row", value).apply()

    var preferTagUid: Boolean
        get() = prefs.getBoolean("prefer_tag_uid", false)
        set(value) = prefs.edit().putBoolean("prefer_tag_uid", value).apply()

    fun getRules(): List<DataModifierRule> {
        val raw = prefs.getString("data_rules", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        DataModifierRule(
                            pattern = obj.getString("pattern"),
                            replacement = obj.getString("replacement")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRules(rules: List<DataModifierRule>) {
        val arr = JSONArray()
        rules.forEach { rule ->
            arr.put(JSONObject().apply {
                put("pattern", rule.pattern)
                put("replacement", rule.replacement)
            })
        }
        prefs.edit().putString("data_rules", arr.toString()).apply()
    }

    fun getQuickTexts(): List<String> {
        val raw = prefs.getString("quick_texts", null)
            ?: return listOf("OK", "CONFERMATO", "LOTTO-", "MAGAZZINO-01")
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveQuickTexts(items: List<String>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString("quick_texts", arr.toString()).apply()
    }
}
