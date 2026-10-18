package com.scannerkeyboard.app.service

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.scannerkeyboard.app.R
import com.scannerkeyboard.app.data.PreferencesRepository
import com.scannerkeyboard.app.data.ScanResultBus
import com.scannerkeyboard.app.data.ScanResultProcessor
import com.scannerkeyboard.app.ui.MainActivity
import com.scannerkeyboard.app.ui.scanner.NfcScannerActivity
import com.scannerkeyboard.app.ui.scanner.ScannerActivity

class ScannerInputMethodService : InputMethodService(), ScanResultBus.Listener {

    private lateinit var prefs: PreferencesRepository
    private var rootView: View? = null
    private var rowsContainer: LinearLayout? = null
    private var quickTextScroll: HorizontalScrollView? = null
    private var quickTextContainer: LinearLayout? = null

    private var isSymbolsMode = false
    private var isShiftActive = false
    private var isCapsLock = false
    private var lastShiftTapTime = 0L
    private var autoScanTriggeredForField = false

    private val handler = Handler(Looper.getMainLooper())
    private val backspaceRepeatRunnable = object : Runnable {
        override fun run() {
            sendBackspace()
            handler.postDelayed(this, 65L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesRepository(this)
        ScanResultBus.register(this)
    }

    override fun onDestroy() {
        ScanResultBus.unregister(this)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        rootView = view
        rowsContainer = view.findViewById(R.id.keyboardRowsContainer)
        quickTextScroll = view.findViewById(R.id.quickTextScroll)
        quickTextContainer = view.findViewById(R.id.quickTextContainer)

        view.findViewById<Button>(R.id.btnScanBarcode).setOnClickListener {
            launchCameraScanner(mode = ScannerActivity.MODE_BARCODE)
        }
        view.findViewById<Button>(R.id.btnScanOcr).setOnClickListener {
            launchCameraScanner(mode = ScannerActivity.MODE_OCR)
        }
        view.findViewById<Button>(R.id.btnScanNfc).setOnClickListener {
            launchNfcScanner()
        }
        view.findViewById<Button>(R.id.btnQuickTextToggle).setOnClickListener {
            toggleQuickTextStrip()
        }
        view.findViewById<Button>(R.id.btnImeSettings).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        populateQuickTexts()
        renderKeyboardRows()
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        populateQuickTexts()
        renderKeyboardRows()

        if (!restarting && prefs.autoScanOnFocus && !autoScanTriggeredForField) {
            autoScanTriggeredForField = true
            launchCameraScanner(ScannerActivity.MODE_BARCODE)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        autoScanTriggeredForField = false
    }

    override fun onScanResult(rawValue: String, sourceType: String) {
        val ic = currentInputConnection ?: return
        val processed = ScanResultProcessor.process(rawValue, prefs)

        ic.beginBatchEdit()
        ic.commitText(processed.finalText, 1)
        ic.endBatchEdit()

        when {
            processed.sendEnterKey -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
            processed.sendTabKey -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
            }
            processed.performEditorAction -> {
                val actionId = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                    ?: EditorInfo.IME_ACTION_DONE
                if (actionId != EditorInfo.IME_ACTION_NONE) {
                    ic.performEditorAction(actionId)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            }
        }
    }

    private fun launchCameraScanner(mode: String) {
        val intent = Intent(this, ScannerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(ScannerActivity.EXTRA_SCAN_MODE, mode)
        }
        startActivity(intent)
    }

    private fun launchNfcScanner() {
        val intent = Intent(this, NfcScannerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun toggleQuickTextStrip() {
        val scroll = quickTextScroll ?: return
        if (scroll.visibility == View.VISIBLE) {
            scroll.visibility = View.GONE
        } else {
            populateQuickTexts()
            scroll.visibility = View.VISIBLE
        }
    }

    private fun populateQuickTexts() {
        val container = quickTextContainer ?: return
        container.removeAllViews()
        for (snippet in prefs.getQuickTexts()) {
            val chip = Button(this).apply {
                text = snippet
                isAllCaps = false
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setBackgroundColor(Color.parseColor("#2B2D31"))
                setPadding(dp(10), dp(2), dp(10), dp(2))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(30)
                ).apply {
                    marginEnd = dp(6)
                }
                layoutParams = lp
                setOnClickListener {
                    currentInputConnection?.commitText(snippet, 1)
                }
            }
            container.addView(chip)
        }
    }

    private fun renderKeyboardRows() {
        val container = rowsContainer ?: return
        container.removeAllViews()

        // Optional Top Number Row (0-9)
        if (prefs.showNumberRow && !isSymbolsMode) {
            container.addView(buildCharRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), heightDp = 40))
        }

        if (!isSymbolsMode) {
            val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
            val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
            val row3 = listOf("z", "x", "c", "v", "b", "n", "m")

            container.addView(buildCharRow(row1))
            container.addView(buildCharRow(row2, horizontalPaddingDp = 14))
            container.addView(buildRow3WithShiftAndBackspace(row3))
        } else {
            val sym1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            val sym2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
            val sym3 = listOf("*", "\"", "'", ":", ";", "!", "?")

            container.addView(buildCharRow(sym1))
            container.addView(buildCharRow(sym2))
            container.addView(buildRow3WithShiftAndBackspace(sym3))
        }

        container.addView(buildBottomActionRow())
    }

    private fun buildCharRow(keys: List<String>, heightDp: Int = 46, horizontalPaddingDp: Int = 2): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(5)
            }
            setPadding(dp(horizontalPaddingDp), 0, dp(horizontalPaddingDp), 0)
        }

        for (k in keys) {
            val displayChar = if (!isSymbolsMode && (isShiftActive || isCapsLock)) k.uppercase() else k
            val btn = createKeyButton(
                label = displayChar,
                weight = 1f,
                heightDp = heightDp,
                bgColor = Color.parseColor("#2B2D31")
            ) {
                currentInputConnection?.commitText(displayChar, 1)
                if (isShiftActive && !isCapsLock) {
                    isShiftActive = false
                    renderKeyboardRows()
                }
            }
            row.addView(btn)
        }
        return row
    }

    private fun buildRow3WithShiftAndBackspace(keys: List<String>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(5)
            }
        }

        // Shift / CapsLock Key
        val shiftLabel = when {
            isSymbolsMode -> "ABC"
            isCapsLock -> "⇪"
            isShiftActive -> "⇧"
            else -> "⇧"
        }
        val shiftColor = if (isShiftActive || isCapsLock) Color.parseColor("#2563EB") else Color.parseColor("#383A40")
        val shiftBtn = createKeyButton(
            label = shiftLabel,
            weight = 1.5f,
            heightDp = 46,
            bgColor = shiftColor
        ) {
            if (isSymbolsMode) {
                isSymbolsMode = false
            } else {
                val now = System.currentTimeMillis()
                if (now - lastShiftTapTime < 350L) {
                    isCapsLock = !isCapsLock
                    isShiftActive = isCapsLock
                } else {
                    if (isCapsLock) {
                        isCapsLock = false
                        isShiftActive = false
                    } else {
                        isShiftActive = !isShiftActive
                    }
                }
                lastShiftTapTime = now
            }
            renderKeyboardRows()
        }
        row.addView(shiftBtn)

        for (k in keys) {
            val displayChar = if (!isSymbolsMode && (isShiftActive || isCapsLock)) k.uppercase() else k
            val btn = createKeyButton(
                label = displayChar,
                weight = 1f,
                heightDp = 46,
                bgColor = Color.parseColor("#2B2D31")
            ) {
                currentInputConnection?.commitText(displayChar, 1)
                if (isShiftActive && !isCapsLock) {
                    isShiftActive = false
                    renderKeyboardRows()
                }
            }
            row.addView(btn)
        }

        // Backspace Key with continuous repeat on hold
        val delBtn = createKeyButton(
            label = "⌫",
            weight = 1.5f,
            heightDp = 46,
            bgColor = Color.parseColor("#383A40")
        ) {}
        delBtn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    sendBackspace()
                    handler.postDelayed(backspaceRepeatRunnable, 350L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(backspaceRepeatRunnable)
                    true
                }
                else -> false
            }
        }
        row.addView(delBtn)

        return row
    }

    private fun buildBottomActionRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ?123 / ABC Toggle
        val symToggle = createKeyButton(
            label = if (isSymbolsMode) "ABC" else "?123",
            weight = 1.3f,
            heightDp = 46,
            bgColor = Color.parseColor("#383A40")
        ) {
            isSymbolsMode = !isSymbolsMode
            renderKeyboardRows()
        }
        row.addView(symToggle)

        // Dedicated inline Scanner Key (like TEC-IT Scanner Keyboard's barcode key next to spacebar)
        val inlineScanBtn = createKeyButton(
            label = "📷",
            weight = 1.1f,
            heightDp = 46,
            bgColor = Color.parseColor("#16A34A")
        ) {
            launchCameraScanner(ScannerActivity.MODE_BARCODE)
        }
        inlineScanBtn.setOnLongClickListener {
            launchCameraScanner(ScannerActivity.MODE_OCR)
            true
        }
        row.addView(inlineScanBtn)

        // Comma
        row.addView(
            createKeyButton(
                label = ",",
                weight = 0.9f,
                heightDp = 46,
                bgColor = Color.parseColor("#2B2D31")
            ) {
                currentInputConnection?.commitText(",", 1)
            }
        )

        // Spacebar
        row.addView(
            createKeyButton(
                label = "Scanner Keyboard",
                weight = 3.8f,
                heightDp = 46,
                bgColor = Color.parseColor("#2B2D31")
            ) {
                currentInputConnection?.commitText(" ", 1)
            }
        )

        // Period
        row.addView(
            createKeyButton(
                label = ".",
                weight = 0.9f,
                heightDp = 46,
                bgColor = Color.parseColor("#2B2D31")
            ) {
                currentInputConnection?.commitText(".", 1)
            }
        )

        // Enter / Action Key
        val enterBtn = createKeyButton(
            label = "↵",
            weight = 1.5f,
            heightDp = 46,
            bgColor = Color.parseColor("#2563EB")
        ) {
            val ic = currentInputConnection ?: return@createKeyButton
            val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
        }
        row.addView(enterBtn)

        return row
    }

    private fun createKeyButton(
        label: String,
        weight: Float,
        heightDp: Int,
        bgColor: Int,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.parseColor("#F3F4F6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (label.length > 4) 12f else 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            setBackgroundColor(bgColor)
            layoutParams = LinearLayout.LayoutParams(0, dp(heightDp), weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun sendBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
