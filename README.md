# Scanner Keyboard (Barcode / QR / OCR / NFC)

Tastiera di sistema Android (`InputMethodService`) sviluppata in Kotlin con scanner **Barcode 1D/2D**, **OCR (riconoscimento testo)** e **lettore NFC (NDEF/UID)** integrati direttamente nella tastiera.

## Funzionalità Principali

- **Tastiera di sistema Android (`ScannerInputMethodService`)**:
  - Layout QWERTY completo (maiuscole, Caps-Lock con doppio tap, simboli `?123`, riga numerica `0-9` opzionale, Backspace con ripetizione continua, Invio/Azione).
  - Barra strumenti rapida con pulsanti **📷 Barcode**, **🔤 OCR**, **📲 NFC**, **⚡ Testi Rapidi** e **⚙️ Impostazioni**.
  - Tasto scanner dedicato accanto alla barra spaziatrice.
- **Scanner Fotocamera (CameraX + Google ML Kit)**:
  - Lettura veloce di codici 1D e 2D (QR Code, EAN-13, EAN-8, UPC-A/E, Code 128, Code 39, Code 93, ITF, Codabar, Data Matrix, PDF417, Aztec).
  - Modalità **OCR Testo** in tempo reale con anteprima e inserimento diretto.
  - Controllo Flash/Torcia, feedback acustico (Beep), vibrazione e modalità **Batch / Scansione Continua**.
- **Lettore NFC (`NfcScannerActivity`)**:
  - Lettura di record di testo **NDEF** o dell'**UID esadecimale** del tag NFC.
- **Formattazione Dati, Simulazione Tasti & Regole Regex**:
  - Prefisso e suffisso personalizzabili con supporto ai placeholder `{DATE}`, `{TIME}`, `{ENTER}`, `{TAB}`.
  - Invio automatico del tasto `ENTER`, `TAB` o dell'azione campo (`Search`/`Done`) dopo ogni scansione.
  - Modalità **Auto-Scan** all'apertura/focus di un campo di testo.
  - Regole di sostituzione **Regex** (`DataModifierRule`) e **Testi Rapidi** configurabili.
