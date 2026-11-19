# Prima prova su Windows 11 e Realme 11 Pro+ 5G

Il kit contiene una clip sintetica di cinque secondi, una voce italiana di circa 3,29 secondi e un SRT di riferimento. Il master atteso ha 99 fotogrammi. Usare gli stessi file su Windows e sul telefono. La clip è un motivo di calibrazione e la voce è materiale di test.

## Scaricare

1. Aprire [i pacchetti di Insurance Cinematic Studio AI](https://github.com/StudioMichettiWalter/INSURANCE-CINEMATIC-STUDIO-AI/tree/insurance-studio-download).
2. Aprire `Kit-Collaudo-M0.3.zip` e usare **Download raw file**. Estrarre in una cartella scrivibile sul PC. Scaricare anche `Insurance-Studio-M0.3-Setup.exe` e `Studio-M0.3-r1-arm64-test.apk` dalla stessa pagina.
3. Conservare lo ZIP originale. Per gli SHA-256 dei pacchetti usare `SHA256SUMS-M0.3.txt`. L'APK usa una firma di test diversa dalle prime build. Prima di sostituire un'installazione precedente, salvare eventuali progetti e file che servono.

## Windows 11

1. Installare il setup su Windows x64. Aprire Windows PowerShell e preparare i motori locali. Il comando scarica e verifica i modelli e gli eseguibili. Per riusare i modelli già scaricati, consultare il manuale Windows.

   ```powershell
   & "$env:LOCALAPPDATA\InsuranceCinematicStudioM0\Prepare-Runtime.ps1"
   ```

2. In Esplora file aprire la cartella estratta del kit, scrivere `powershell` nella barra del percorso e premere Invio. Nella finestra eseguire:

   ```powershell
   .\Windows-Collaudo.ps1
   ```

3. Conservare `Esito-Windows-srt-*.zip`. Per provare anche Whisper e Qwen, ripetere con `.\Windows-Collaudo.ps1 -Automatico`. L'esito automatico va valutato separatamente, ascoltando e leggendo il risultato.

## Realme 11 Pro+ 5G

1. Copiare sul telefono APK, `clip.mp4`, `voce.wav` e `riferimento.srt`. Installare l'APK e aprire il Laboratorio M0. Per la prova autonoma scollegare la rete e spegnere il PC una volta copiati i file.
2. Usare **Importa clip video**, **Importa voce WAV o M4A 48 kHz** e **Importa SRT corretto**, in quest'ordine. Selezionare rispettivamente `clip.mp4`, `voce.wav` e `riferimento.srt`. La voce fornita è PCM a 16 kHz ed è supportata.
3. Con la didascalia manuale vuota, premere **Renderizza MP4 verticale**, poi **Salva video, SRT e rapporto ZIP**. Conservare il file `Studio-M0-export.zip`. Eseguire questo primo render SRT prima della prova dei modelli.
4. Per provare anche l'AI locale, copiare i due modelli forniti e importarli dai pulsanti **Importa modello Whisper small Q5_1** e **Importa modello Qwen Q4_K_M**. Premere **Trascrivi narrazione in italiano**, selezionare `voce.wav` e poi premere **Proponi titolo e parola chiave**. Conservare testo e proposta effettivi.

## Raccogliere gli esiti

Trasferire i due ZIP originali. Annotare la versione Android e realme UI, l'architettura del PC, i tempi e gli eventuali errori di installazione o render. Ascoltare i due master e controllare leggibilità e sincronismo. Per RAM, temperature, aggiornamenti e ripresa del lavoro, usare il [protocollo completo](https://github.com/StudioMichettiWalter/INSURANCE-CINEMATIC-STUDIO-AI/blob/main/docs/COLLAUDO.md).

Il confronto diagnostico richiede Python 3.12, NumPy, FFmpeg e ffprobe. Dalla cartella estratta del kit, passare i percorsi effettivi dei due export e del kit originale:

```text
python -m pip install -r requirements-host.lock
python compare_m0.py Esito-Windows-srt-XXXXXXXX.zip Studio-M0-export.zip Kit-Collaudo-M0.3.zip
```

Il rapporto verifica gli hash degli ingressi e del master, i cue rispetto al riferimento, codec, cadenza, durata, differenze visive e audio. Le soglie restano proposte e il rapporto mantiene l'accettazione M0 pendente. Linux può riprodurre i test del kit. Per la prova M0 servono export generati dal PC e dal telefono.
