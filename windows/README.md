# Windows 10/11 x64 • laboratorio M0

L’installer per utente aggiunge «Insurance Cinematic Studio M0» al menu Start. Lo spike richiede Windows PowerShell 5.1 con esecuzione degli script locali permessa e un runtime preparato come descritto sotto, oppure ffmpeg.exe e ffprobe.exe nel PATH. La build FFmpeg deve offrire subtitles/libass, overlay, libx264 e AAC. La pagina https://ffmpeg.org/download.html indica le distribuzioni Windows. Verificare la licenza della distribuzione scelta. Il setup non installa codec né richiede privilegi amministrativi. Per disinstallare usare Applicazioni installate.

Avviare dal menu Start. Selezionare clip, voce, didascalie SRT UTF-8 e un nuovo nome per il master. Il compositing conserva l’inquadratura del clip, aggiunge un contratto 3D renderizzato localmente e sovrappone le didascalie. La voce governa la durata. Lo spike mantiene l’ultimo frame se il clip è corto. Scegliere clip già accordate con la voce. Limite sperimentale di 300 s. La finestra console mostra gli errori di codifica. Ctrl+C interrompe il job. Output preesistenti sono protetti.

Da PowerShell, con i percorsi reali dei propri file:

```powershell
& "$env:LOCALAPPDATA\InsuranceCinematicStudioM0\Export-M0.ps1" -Clip .\clip.mp4 -Voce .\voce.wav -Sottotitoli .\cue.srt -Destinazione .\master-nuovo.mp4
```

L’export verifica i flussi con ffprobe, il numero di fotogrammi, il formato verticale 1080×1920, il rate nominale 30/1, la frequenza AAC a 48 kHz e la durata audio. Restano da controllare misura LUFS e picco, sincronismo percepito, safe area e leggibilità. Il percorso Windows dello spike accetta didascalie già temporizzate. La modalità automatica integra i modelli locali descritti sotto. Il collaudo nativo Windows resta aperto.

Per ricostruire su Linux, preparare un compilatore LLVM-MinGW UCRT x64 e makensis, quindi dalla radice del repository eseguire `bash windows/build.sh`. Impostare WIN_CC e MAKENSIS per specificare i percorsi degli eseguibili. La compilazione produce i file in windows/build. Il runner usa Inter, distribuito con la licenza SIL OFL 1.1 in fonts/OFL.txt. Per versioni e prove consultare qa/windows*.

## Preparazione assistita dei motori locali

Dopo l’installazione aprire Windows PowerShell e avviare lo script seguente. Il download esplicito richiede circa 800 MB oltre allo spazio per l’estrazione. Serve una rete con accesso a GitHub e Hugging Face. Il programma controlla gli SHA-256 e registra soltanto percorsi locali nel profilo dell’utente.

```powershell
$studio = "$env:LOCALAPPDATA\InsuranceCinematicStudioM0"
& "$studio\Prepare-Runtime.ps1"
```

Con il kit offline di pesi verificati, passare allo stesso comando `-WhisperModel 'D:\modelli\ggml-small-q5_1.bin' -QwenModel 'D:\modelli\qwen.gguf'`. Si scaricano solo i pacchetti degli eseguibili mancanti. Per preparare un PC privo di rete è possibile copiare gli archivi originali nelle posizioni indicate sotto, quindi eseguire il comando con i percorsi dei modelli.

| File sotto %LOCALAPPDATA%\InsuranceStudioM0-Runtime | Fonte fissata |
|---|---|
| whisper-v1.7.6.zip | whisper.cpp v1.7.6 Windows x64 CPU |
| llama-b6000.zip | llama.cpp b6000 win-cpu-x64 |
| ffmpeg-essentials-9.0.2.zip | Gyan FFmpeg essentials 9.0.2 |

Gli URL e SHA-256 sono in asr.lock.json, dependencies.lock.json e windows.lock.json accanto al programma. Il setup distribuisce il codice e Inter. Lo script scarica FFmpeg dall’editore indicato dalla pagina ufficiale FFmpeg. I termini della distribuzione e dei codec per il contesto d’uso devono essere verificati prima della release. Dopo la preparazione, dal menu Start scegliere clip e voce, quindi «Sì» per la trascrizione e l’inferenza locale. Per un export guidato verificabile si può scegliere «No» e fornire un SRT. La modalità automatica usa Whisper italiano, genera SRT e sottopone la prima frase a Qwen con schema JSON. Il titolo e la parola vengono salvati nel rapporto accanto al file; la parola è evidenziata quando presente nelle didascalie. Il testo viene collocato entro due righe. I cue troppo lunghi fermano il rendering. Il filtro audio mira a -16 LUFS e -1,5 dBTP. La verifica finale richiede la misura della traccia codificata e l’ascolto.

```powershell
& "$studio\Export-M0.ps1" -Clip 'D:\riprese\clip.mp4' -Voce 'D:\riprese\voce.wav' -Destinazione 'D:\montaggi\master.mp4'
```

Se l’avvio della GUI fallisce, usare questo comando da una console per conservare il messaggio di errore. Su computer con policy aziendale che richiede firma Authenticode, chiedere all’amministratore di convalidare lo script. Il setup M0 è privo di firma. I modelli rimangono nella cartella runtime separata dopo la disinstallazione.
