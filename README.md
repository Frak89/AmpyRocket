# AmpyRocket 🚀
Launcher Android cyberpunk / futuristico della famiglia Ampy (stile AmpyRAW Cyberdark, AmpyLink). Android 8 → Android 17.

## Scarica
Vai in **Releases → latest** e scarica `AmpyRocket.apk` dal telefono (oppure Actions → ultimo build → artifact `AmpyRocket-APK`).
Dopo l'installazione: **Impostazioni AmpyRocket → Launcher predefinito**.

## Funzioni launcher classiche
- Home a pagine con griglia configurabile (4–6 colonne, 5–7 righe), dock fino a 6 app
- Drawer A→Z con ricerca, barra lettere, ordinamento (A-Z, più usate, recenti, nuove)
- Widget Android (selettore, bind, configurazione, rimozione)
- Cartelle, sposta/rinomina/nascondi app, disinstalla, info app
- Scorciatoie app (app shortcuts) sul tocco lungo
- Badge notifiche, profilo di lavoro, nuove app aggiunte in automatico
- Gesti: su, giù, doppio tocco, due dita su/giù (drawer, task, notifiche, blocco schermo…)

## Extra AmpyRocket
- **Testo glitch**: le lettere si decodificano cambiando glifo (A-Z, simboli, katakana) con split RGB e strappi orizzontali
- **Transizioni pagine**: cubo 3D (Compiz), flip card, stack, carousel, rotazione, zoom, glitch slice, tablet
- **Sfondi animati**: griglia synthwave con sole neon, pioggia digitale, rete di particelle, alveare esagonale, warp starfield, scanlines CRT
- **Apertura app**: Neon Genie Morph, clip reveal, scale up
- **Ricerca-calcolatrice** (`12*7+3`) e ricerca web
- **Orologio glitch** (anello secondi, batteria, sveglia) + widget orologio per altri launcher
- **HUD di sistema**: CPU, RAM, batteria, disco con grafici
- **Lockscreen custom** sopra il blocco di sistema (PIN/impronta restano attivi), con notifiche
- 6 temi: Cyberpunk, Windows Vista Aero, Futuristic Glass, Retro, Minimal, Blood Moon
- Backup/ripristino del layout in JSON

## Task drawer / multitasking (⧉ TASK)
- Card 3D: **Flip 3D Windows Vista**, **stack in profondità (Linux)**, **Cover Flow**, **cubo rotante Compiz**, **elica**, **Exposé**
- Scorri una card in su per chiuderla: **Genie / lampada magica**, glitch dissolve, riduci, pixel burn
- Tieni premuto: la card si **gira** e mostra i dettagli; effetto **wobbly** a molla mentre scorri
- Grafici e valori reali di **CPU** (per core) e **RAM** di sistema, batteria e temperatura
- Per ogni app: stato (primo piano / servizio / background), attività negli ultimi 15 minuti con grafico, ultimo uso
- Vista tabella con tutte le app attive

Nota: Android non permette a un'app normale di leggere CPU/RAM delle singole altre app; per app viene mostrata l'attività d'uso (serve "Accesso ai dati di utilizzo").

## Permessi (tutti facoltativi, dal pannello Impostazioni)
Accesso utilizzo (task drawer) · Accesso notifiche (badge, lockscreen) · Accessibilità (blocco schermo e tendina con i gesti) · Sovrapposizione (lockscreen) · Notifiche.

## Build
GitHub Actions compila a ogni push (`.github/workflows/android-apk.yml`). Firma stabile opzionale con i secrets
`AMPYROCKET_KEYSTORE_B64`, `AMPYROCKET_STORE_PASSWORD`, `AMPYROCKET_KEY_ALIAS`, `AMPYROCKET_KEY_PASSWORD`
(senza secrets l'APK è firmato con chiave debug: per aggiornare può servire disinstallare la versione precedente).
In locale: Android Studio, JDK 17, Gradle 8.13, SDK 36.
