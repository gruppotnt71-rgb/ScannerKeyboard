package com.scannerkeyboard.app.data

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object ScanResultBus {
    fun interface Listener {
        fun onScanResult(rawValue: String, sourceType: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()

    fun register(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregister(listener: Listener) {
        listeners.remove(listener)
    }

    fun publish(rawValue: String, sourceType: String) {
        mainHandler.post {
            for (listener in listeners) {
                listener.onScanResult(rawValue, sourceType)
            }
        }
    }
}

object ScanResultProcessor {
    data class ProcessedScan(
        val finalText: String,
        val sendEnterKey: Boolean,
        val sendTabKey: Boolean,
        val performEditorAction: Boolean
    )

    fun process(rawValue: String, repo: PreferencesRepository): ProcessedScan {
        var transformed = rawValue

        // 1. Apply regex modification rules in order (like DataModifierListEditor in Scanner Keyboard)
        for (rule in repo.getRules()) {
            if (rule.pattern.isNotEmpty()) {
                transformed = try {
                    Regex(rule.pattern).replace(transformed, rule.replacement)
                } catch (_: Exception) {
                    transformed.replace(rule.pattern, rule.replacement)
                }
            }
        }

        // 2. Expand dynamic placeholders in prefix and suffix
        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        fun expandPlaceholders(input: String): String {
            return input
                .replace("{DATE}", dateStr, ignoreCase = true)
                .replace("{TIME}", timeStr, ignoreCase = true)
                .replace("\\n", "\n")
                .replace("\\t", "\t")
        }

        var prefixExpanded = expandPlaceholders(repo.prefix)
        var suffixExpanded = expandPlaceholders(repo.suffix)

        var inlineEnter = false
        var inlineTab = false

        if (prefixExpanded.contains("{ENTER}", ignoreCase = true) ||
            suffixExpanded.contains("{ENTER}", ignoreCase = true)
        ) {
            inlineEnter = true
            prefixExpanded = prefixExpanded.replace("{ENTER}", "", ignoreCase = true)
            suffixExpanded = suffixExpanded.replace("{ENTER}", "", ignoreCase = true)
        }

        if (prefixExpanded.contains("{TAB}", ignoreCase = true) ||
            suffixExpanded.contains("{TAB}", ignoreCase = true)
        ) {
            inlineTab = true
            prefixExpanded = prefixExpanded.replace("{TAB}", "", ignoreCase = true)
            suffixExpanded = suffixExpanded.replace("{TAB}", "", ignoreCase = true)
        }

        val action = repo.postScanAction
        return ProcessedScan(
            finalText = prefixExpanded + transformed + suffixExpanded,
            sendEnterKey = inlineEnter || action == PostScanAction.ENTER,
            sendTabKey = inlineTab || action == PostScanAction.TAB,
            performEditorAction = action == PostScanAction.EDITOR_ACTION
        )
    }
}
