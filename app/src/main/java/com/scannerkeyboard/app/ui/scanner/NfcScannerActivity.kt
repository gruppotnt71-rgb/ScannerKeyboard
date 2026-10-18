package com.scannerkeyboard.app.ui.scanner

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.scannerkeyboard.app.R
import com.scannerkeyboard.app.data.PreferencesRepository
import com.scannerkeyboard.app.data.ScanResultBus
import java.nio.charset.Charset

class NfcScannerActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var prefs: PreferencesRepository
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var tvNfcStatus: TextView
    private lateinit var cbPreferTagUid: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_scanner)

        prefs = PreferencesRepository(this)
        tvNfcStatus = findViewById(R.id.tvNfcStatus)
        cbPreferTagUid = findViewById(R.id.cbPreferTagUid)

        cbPreferTagUid.isChecked = prefs.preferTagUid
        cbPreferTagUid.setOnCheckedChangeListener { _, isChecked ->
            prefs.preferTagUid = isChecked
        }

        findViewById<Button>(R.id.btnCancelNfc).setOnClickListener {
            finish()
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            tvNfcStatus.text = "Questo dispositivo non dispone di hardware NFC."
        } else if (nfcAdapter?.isEnabled == false) {
            tvNfcStatus.text = "L'NFC è disattivato nelle impostazioni di sistema."
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return

        val uidHex = tag.id.joinToString("") { "%02X".format(it) }
        val resultValue = if (prefs.preferTagUid) {
            uidHex
        } else {
            readNdefText(tag) ?: uidHex
        }

        runOnUiThread {
            ScanResultBus.publish(resultValue, "NFC")
            Toast.makeText(this, "NFC letto: $resultValue", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun readNdefText(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            val message: NdefMessage = ndef.ndefMessage ?: return null
            val record = message.records.firstOrNull() ?: return null
            parseNdefRecord(record)
        } catch (_: Exception) {
            null
        } finally {
            try {
                ndef.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun parseNdefRecord(record: NdefRecord): String? {
        val payload = record.payload
        if (payload.isEmpty()) return null

        return if (record.tnf == NdefRecord.TNF_WELL_KNOWN &&
            record.type.contentEquals(NdefRecord.RTD_TEXT)
        ) {
            val statusByte = payload[0].toInt()
            val encoding = if ((statusByte and 0x80) == 0) "UTF-8" else "UTF-16"
            val langCodeLength = statusByte and 0x3F
            String(
                payload,
                langCodeLength + 1,
                payload.size - langCodeLength - 1,
                Charset.forName(encoding)
            )
        } else {
            String(payload, Charsets.UTF_8).trim()
        }
    }
}
