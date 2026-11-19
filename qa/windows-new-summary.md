# Gate export Windows M0.3
Prove eseguite sul banco Linux con PowerShell 7.4.13, FFmpeg 7.1.5 e renderer C compilato sull'host. Installazione ed esecuzione Windows nativa NON ESEGUITO.

- PASS integrazione dal WAV italiano e SRT accettato, clip testsrc, contratto 3D, codifica H.264 1080×1920, 99 frame CFR, AAC48k a 192 kbit/s.
- PASS misure del file finale. Loudness -16,86 LUFS e true peak -1,35 dBTP. Nessun intervallo nero rilevato. Vedere windows-new-repro.json.
- PASS controlli negativi, fixture nera e silenzio nello strumento QA.
- PASS blocco della narrazione muta nella normale invocazione del driver, nessun master finale scritto.
- PASS export già esistente preservato con identità dei byte dopo tentativo di seconda esecuzione sulla stessa destinazione.
- PASS cross-build setup Windows x64 M0.3, hash in windows-new-build.log.

Il gate ha individuato nella versione precedente -17,41 LUFS e -0,52 dBTP sulla stessa voce. Era applicato un solo passaggio loudnorm e AAC senza bitrate fissato. La correzione allinea normalizzazione a due passaggi e bitrate alla pipeline host. Il controllo sul file codificato resta vincolante perché l'encoder può introdurre overshoot.

Per ripetere la prova eseguire python3 windows/tests/host.py --pwsh /percorso/pwsh. I file sintetici vengono creati nella directory temporanea del test e rimossi al termine. Configurare directory XDG scrivibili se il profilo host è in sola lettura. Il test di QA continua a richiedere ascolto e ispezione umana per qualità e sincronismo.
