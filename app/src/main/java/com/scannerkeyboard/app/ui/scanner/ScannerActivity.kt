package com.scannerkeyboard.app.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scannerkeyboard.app.R
import com.scannerkeyboard.app.data.PreferencesRepository
import com.scannerkeyboard.app.data.ScanResultBus
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCAN_MODE = "extra_scan_mode"
        const val MODE_BARCODE = "BARCODE"
        const val MODE_OCR = "OCR"
        private const val REQ_CAMERA = 1001
    }

    private lateinit var prefs: PreferencesRepository
    private lateinit var previewView: PreviewView
    private lateinit var tvScanStatus: TextView
    private lateinit var tvOcrPreview: TextView
    private lateinit var btnConfirmOcr: Button
    private lateinit var cameraExecutor: ExecutorService

    private var camera: Camera? = null
    private var torchEnabled = false
    private var currentMode = MODE_BARCODE
    private var lastRecognizedOcrText = ""
    private var lastScanTimestamp = 0L
    private var isFinishingScan = false

    private val barcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        prefs = PreferencesRepository(this)
        currentMode = intent.getStringExtra(EXTRA_SCAN_MODE) ?: MODE_BARCODE
        torchEnabled = prefs.torchAtStart

        previewView = findViewById(R.id.previewView)
        tvScanStatus = findViewById(R.id.tvScanStatus)
        tvOcrPreview = findViewById(R.id.tvOcrPreview)
        btnConfirmOcr = findViewById(R.id.btnConfirmOcr)

        findViewById<Button>(R.id.btnModeBarcode).setOnClickListener {
            switchMode(MODE_BARCODE)
        }
        findViewById<Button>(R.id.btnModeOcr).setOnClickListener {
            switchMode(MODE_OCR)
        }
        findViewById<Button>(R.id.btnTorch).setOnClickListener {
            torchEnabled = !torchEnabled
            camera?.cameraControl?.enableTorch(torchEnabled)
        }
        findViewById<Button>(R.id.btnCloseScanner).setOnClickListener {
            finish()
        }
        btnConfirmOcr.setOnClickListener {
            val text = lastRecognizedOcrText.trim()
            if (text.isNotEmpty()) {
                triggerFeedback()
                ScanResultBus.publish(text, MODE_OCR)
                if (!prefs.continuousScan) {
                    finish()
                } else {
                    Toast.makeText(this, "Inserito: $text", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        switchMode(currentMode)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
        }
    }

    private fun switchMode(mode: String) {
        currentMode = mode
        if (mode == MODE_BARCODE) {
            tvScanStatus.text = if (prefs.continuousScan) {
                "Modalità Barcode/QR Continua (Batch): inquadra i codici..."
            } else {
                "Inquadra un codice a barre 1D o 2D (QR, EAN, Code128, DataMatrix)..."
            }
            tvOcrPreview.visibility = View.GONE
            btnConfirmOcr.visibility = View.GONE
        } else {
            tvScanStatus.text = "Modalità OCR: inquadra il testo e premi 'Inserisci Testo Riconosciuto'"
            tvOcrPreview.visibility = View.VISIBLE
            btnConfirmOcr.visibility = View.VISIBLE
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                if (torchEnabled) {
                    camera?.cameraControl?.enableTorch(true)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Errore avvio fotocamera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isFinishingScan) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        if (currentMode == MODE_BARCODE) {
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    val first = barcodes.firstOrNull { !it.rawValue.isNullOrEmpty() }
                    val raw = first?.rawValue
                    if (!raw.isNullOrEmpty()) {
                        val now = System.currentTimeMillis()
                        if (now - lastScanTimestamp > 1200L) {
                            lastScanTimestamp = now
                            onBarcodeDetected(raw)
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val cleaned = visionText.text.trim().replace("\n", " ")
                    if (cleaned.isNotEmpty()) {
                        lastRecognizedOcrText = cleaned
                        tvOcrPreview.text = cleaned
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun onBarcodeDetected(rawValue: String) {
        triggerFeedback()
        ScanResultBus.publish(rawValue, MODE_BARCODE)

        if (prefs.continuousScan) {
            tvScanStatus.text = "Letto: $rawValue"
        } else {
            isFinishingScan = true
            finish()
        }
    }

    private fun triggerFeedback() {
        if (prefs.beepOnScan) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            } catch (_: Exception) {
            }
        }
        if (prefs.vibrateOnScan) {
            try {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(70L)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Permesso fotocamera necessario per lo scanner", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
