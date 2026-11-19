# Pacchetti Insurance Cinematic Studio AI

## Checkpoint corrente M0.3
- [Kit per la prima prova Windows e Realme](Kit-Collaudo-M0.3.zip), con clip, voce, SRT e guida. Aprire il file e usare **Download raw file** per scaricare solo il kit.
- Windows x64: Insurance-Studio-M0.3-Setup.exe.
- Android ARM64: Studio-M0.3-r1-arm64-test.apk, firma effimera di test.
- Sorgenti aggiornati: Insurance-Cinematic-Studio-M0.3-sorgenti.zip.
- Esiti e limiti: STATUS-M0.3.md.
- Integrità: SHA256SUMS-M0.3.txt.

Sorgenti navigabili nel ramo main. Dal pulsante Code scegliere Download ZIP per scaricare questo ramo completo, poi estrarre la cartella.
I pacchetti M0.1 sono conservati come storico. I due checkpoint condividono gli stessi modelli locali.
M0 richiede ancora collaudo fisico Windows e Android, misure termiche e confronto di parità.

## Novità M0.3
Android importa sottotitoli SRT corretti e salva ZIP con master.mp4, captions.srt e qa.json.
I segmenti lunghi o con tempi incoerenti fermano il render e richiedono correzione.
Il parser WAV applica controlli RIFF e PCM prima della codifica.
Windows misura livelli e picchi sull'AAC finale e blocca silenzio, livelli fuori soglia e immagini nere.
L'APK di test ha una firma diversa da quella precedente. Conservare i dati prima di pianificare una nuova installazione.
Per il collaudo sul Realme 11 Pro+ 5G e Windows 11 consultare docs/HARDWARE.md nei sorgenti.

## Modelli locali
Le nove parti Modelli-locali-M0.zip.part001 ... part009 ricompongono il kit.
Da PowerShell nella cartella estratta eseguire:
powershell -NoProfile -File .\Ricostruisci-Modelli.ps1

Lo script crea Modelli-locali-M0.zip e verifica SHA-256. Estrarre il kit per ottenere Whisper small Q5_1 e Qwen GGUF con licenze e lockfile.
Servono spazio per download, cartella estratta, archivio ricomposto e modelli estratti.
Se la policy PowerShell impedisce l'esecuzione far verificare lo script dall'amministratore.
Gli hash dei file storici e delle parti sono in SHA256SUMS-download.txt.
