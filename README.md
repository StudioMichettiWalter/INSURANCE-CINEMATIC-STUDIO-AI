# Pacchetti Insurance Cinematic Studio M0
Scaricare questo ramo con Code > Download ZIP ed estrarre la cartella sul PC.
I sorgenti sono nel ramo main.
Il checkpoint rimane M0 BLOCKED per collaudo fisico e parità. APK con firma di test, setup Windows privo di Authenticode.

## File
- Insurance-Studio-M0-Setup.exe per Windows x64.
- Studio-M0-arm64-test.apk per Android ARM64.
- Insurance-Cinematic-Studio-sorgenti.zip per sorgenti e istruzioni.
- Collaudo-host-M0.mp4 per il filmato sintetico di collaudo Linux.
- STATUS.md per prove e limiti.
- SHA256SUMS-download.txt per gli hash dei file e delle parti.

## Modelli
Le nove parti Modelli-locali-M0.zip.part001 ... part009 ricompongono il kit da 650 MiB.
Da PowerShell nella cartella estratta eseguire:
powershell -NoProfile -File .\Ricostruisci-Modelli.ps1

Lo script crea il kit ZIP e verifica SHA-256. Estrarre il kit per ottenere Whisper small Q5_1 e Qwen GGUF con licenze e lockfile.
Servono spazio per download, cartella estratta, archivio ricomposto e modelli estratti.
Se la policy PowerShell impedisce l'esecuzione, far verificare lo script dall'amministratore.
Le nove parti possono essere concatenate anche con strumenti binari e verificate contro SHA256SUMS.txt.
