package com.scannerkeyboard.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.scannerkeyboard.app.R
import com.scannerkeyboard.app.data.DataModifierRule
import com.scannerkeyboard.app.data.PostScanAction
import com.scannerkeyboard.app.data.PreferencesRepository
import com.scannerkeyboard.app.data.ScanResultBus
import com.scannerkeyboard.app.data.ScanResultProcessor
import com.scannerkeyboard.app.ui.scanner.ScannerActivity

class MainActivity : AppCompatActivity(), ScanResultBus.Listener {

    private lateinit var prefs: PreferencesRepository
    private lateinit var tvSetupStatus: TextView
    private lateinit var etTestInput: EditText
    private lateinit var etPrefix: EditText
    private lateinit var etSuffix: EditText
    private lateinit var spPostScanAction: Spinner
    private lateinit var cbBeepOnScan: CheckBox
    private lateinit var cbVibrateOnScan: CheckBox
    private lateinit var cbTorchAtStart: CheckBox
    private lateinit var cbContinuousScan: CheckBox
    private lateinit var cbAutoScanOnFocus: CheckBox
    private lateinit var cbShowNumberRow: CheckBox
    private lateinit var rulesListContainer: LinearLayout
    private lateinit var quickTextsListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesRepository(this)
        ScanResultBus.register(this)

        tvSetupStatus = findViewById(R.id.tvSetupStatus)
        etTestInput = findViewById(R.id.etTestInput)
        etPrefix = findViewById(R.id.etPrefix)
        etSuffix = findViewById(R.id.etSuffix)
        spPostScanAction = findViewById(R.id.spPostScanAction)
        cbBeepOnScan = findViewById(R.id.cbBeepOnScan)
        cbVibrateOnScan = findViewById(R.id.cbVibrateOnScan)
        cbTorchAtStart = findViewById(R.id.cbTorchAtStart)
        cbContinuousScan = findViewById(R.id.cbContinuousScan)
        cbAutoScanOnFocus = findViewById(R.id.cbAutoScanOnFocus)
        cbShowNumberRow = findViewById(R.id.cbShowNumberRow)
        rulesListContainer = findViewById(R.id.rulesListContainer)
        quickTextsListContainer = findViewById(R.id.quickTextsListContainer)

        setupWizardButtons()
        setupTestButtons()
        loadPreferencesIntoViews()
        renderRulesList()
        renderQuickTextsList()
    }

    override fun onResume() {
        super.onResume()
        updateSetupStatus()
    }

    override fun onDestroy() {
        ScanResultBus.unregister(this)
        super.onDestroy()
    }

    override fun onScanResult(rawValue: String, sourceType: String) {
        // If our IME is not the active keyboard yet, append directly to the test field so the user can test immediately
        if (!isOurImeSelected()) {
            val processed = ScanResultProcessor.process(rawValue, prefs)
            val suffixBreak = if (processed.sendEnterKey) "\n" else if (processed.sendTabKey) "\t" else ""
            etTestInput.append(processed.finalText + suffixBreak)
        }
    }

    private fun setupWizardButtons() {
        findViewById<Button>(R.id.btnStepEnableIme).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnStepSelectIme).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        findViewById<Button>(R.id.btnStepGrantCamera).setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 2001)
        }
    }

    private fun setupTestButtons() {
        findViewById<Button>(R.id.btnTestBarcode).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java).apply {
                putExtra(ScannerActivity.EXTRA_SCAN_MODE, ScannerActivity.MODE_BARCODE)
            }
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnTestOcr).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java).apply {
                putExtra(ScannerActivity.EXTRA_SCAN_MODE, ScannerActivity.MODE_OCR)
            }
            startActivity(intent)
        }
    }

    private fun loadPreferencesIntoViews() {
        etPrefix.setText(prefs.prefix)
        etSuffix.setText(prefs.suffix)

        val actions = PostScanAction.values()
        spPostScanAction.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            actions.map { it.label }
        )
        spPostScanAction.setSelection(actions.indexOf(prefs.postScanAction).coerceAtLeast(0))

        cbBeepOnScan.isChecked = prefs.beepOnScan
        cbVibrateOnScan.isChecked = prefs.vibrateOnScan
        cbTorchAtStart.isChecked = prefs.torchAtStart
        cbContinuousScan.isChecked = prefs.continuousScan
        cbAutoScanOnFocus.isChecked = prefs.autoScanOnFocus
        cbShowNumberRow.isChecked = prefs.showNumberRow

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            prefs.prefix = etPrefix.text.toString()
            prefs.suffix = etSuffix.text.toString()
            prefs.postScanAction = PostScanAction.values()[spPostScanAction.selectedItemPosition]
            prefs.beepOnScan = cbBeepOnScan.isChecked
            prefs.vibrateOnScan = cbVibrateOnScan.isChecked
            prefs.torchAtStart = cbTorchAtStart.isChecked
            prefs.continuousScan = cbContinuousScan.isChecked
            prefs.autoScanOnFocus = cbAutoScanOnFocus.isChecked
            prefs.showNumberRow = cbShowNumberRow.isChecked
            Toast.makeText(this, "Impostazioni salvate!", Toast.LENGTH_SHORT).show()
        }

        val etRulePattern = findViewById<EditText>(R.id.etRulePattern)
        val etRuleReplacement = findViewById<EditText>(R.id.etRuleReplacement)
        findViewById<Button>(R.id.btnAddRule).setOnClickListener {
            val pattern = etRulePattern.text.toString()
            val replacement = etRuleReplacement.text.toString()
            if (pattern.isNotEmpty()) {
                val updated = prefs.getRules().toMutableList()
                updated.add(DataModifierRule(pattern, replacement))
                prefs.saveRules(updated)
                etRulePattern.setText("")
                etRuleReplacement.setText("")
                renderRulesList()
            }
        }

        val etQuickTextSnippet = findViewById<EditText>(R.id.etQuickTextSnippet)
        findViewById<Button>(R.id.btnAddQuickText).setOnClickListener {
            val text = etQuickTextSnippet.text.toString().trim()
            if (text.isNotEmpty()) {
                val updated = prefs.getQuickTexts().toMutableList()
                updated.add(text)
                prefs.saveQuickTexts(updated)
                etQuickTextSnippet.setText("")
                renderQuickTextsList()
            }
        }
    }

    private fun renderRulesList() {
        rulesListContainer.removeAllViews()
        val rules = prefs.getRules()
        rules.forEachIndexed { idx, rule ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val tv = TextView(this).apply {
                text = "'${rule.pattern}' ➔ '${rule.replacement}'"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnDel = Button(this).apply {
                text = "✖"
                setOnClickListener {
                    val updated = prefs.getRules().toMutableList()
                    updated.removeAt(idx)
                    prefs.saveRules(updated)
                    renderRulesList()
                }
            }
            row.addView(tv)
            row.addView(btnDel)
            rulesListContainer.addView(row)
        }
    }

    private fun renderQuickTextsList() {
        quickTextsListContainer.removeAllViews()
        val items = prefs.getQuickTexts()
        items.forEachIndexed { idx, snippet ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val tv = TextView(this).apply {
                text = snippet
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val btnDel = Button(this).apply {
                text = "✖"
                setOnClickListener {
                    val updated = prefs.getQuickTexts().toMutableList()
                    updated.removeAt(idx)
                    prefs.saveQuickTexts(updated)
                    renderQuickTextsList()
                }
            }
            row.addView(tv)
            row.addView(btnDel)
            quickTextsListContainer.addView(row)
        }
    }

    private fun updateSetupStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imeEnabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val imeSelected = isOurImeSelected()
        val camGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        val s1 = if (imeEnabled) "✅ Abilitata" else "❌ Non abilitata"
        val s2 = if (imeSelected) "✅ Attiva" else "⚠️ Non selezionata"
        val s3 = if (camGranted) "✅ Concesso" else "❌ Da concedere"

        tvSetupStatus.text = "Stato: 1) Tastiera: $s1  |  2) Metodo Input: $s2  |  3) Fotocamera: $s3"
    }

    private fun isOurImeSelected(): Boolean {
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return defaultIme?.startsWith("$packageName/") == true
    }
}
