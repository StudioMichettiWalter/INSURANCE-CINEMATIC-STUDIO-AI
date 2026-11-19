# Insurance Cinematic Studio AI

[Scarica i pacchetti Windows, Android e i modelli](https://github.com/StudioMichettiWalter/INSURANCE-CINEMATIC-STUDIO-AI/tree/insurance-studio-download). Nella pagina aprire **Code > Download ZIP**, estrarre la cartella e seguire README.md. Per i modelli usare Ricostruisci-Modelli.ps1.

Per la prima prova su Windows 11 e Realme, usa il [kit M0.3 con clip, voce e sottotitoli di riferimento](docs/COLLAUDO-RAPIDO.md).
**Checkpoint tecnico 0.1.0-m0.3 per Walter Michetti.** Iniziare da STATUS.md per gli esiti. La specifica accettata e le preferenze editoriali originali sono conservate integralmente in source/. La scheda installabile delle regole è docs/regole-prodotto.json. Il template manifest v1 è stato scritto per questo repository perché mancava nello ZIP originale.

## Uso dei pacchetti di prova
- Windows x64. Verificare l'hash dell'installer e seguire windows/README.md per predisporre i motori CPU, FFmpeg, ffprobe e i modelli. Lanciare il setup per utente e aprire il laboratorio M0. Scegliere i file locali, il percorso di lavoro e un nuovo nome di export.
- Android ARM64. Verificare l'hash dell'APK, copiarlo sul dispositivo di collaudo ed aprirlo dal gestore file. Se Android lo richiede, autorizzare l'installazione da quella sorgente. Il certificato di test serve al collaudo. Per la firma del proprietario usare il percorso documentato in android/. Conservare e proteggere il keystore fuori dal repository.
- Modelli. Estrarre Modelli-locali-M0.zip su ciascun dispositivo. Il peso complessivo dei due file è 681.485.519 byte. Lo spazio necessario durante importazione e rendering è superiore. Selezionare il file Whisper .bin per il parlato e il GGUF Qwen per inferenza. Le copie delle licenze e gli hash sono inclusi nel kit. Per acquisirli separatamente usare gli URL immutabili nei lockfile.
- Nel primo collaudo usare una voce WAV PCM mono 16 kHz e una clip MP4 H.264 verticale, almeno lunga quanto la voce. La fixture è in tests/asr. La clip si produce con il comando FFmpeg di tests/host-spike.sh. I contenuti di prova restano nell'area test e non vengono importati all'avvio dell'app.
- Prima di considerare pubblicabile un progetto assicurativo verificare frase, fonti, clausole, CTA, sincronismo e autorizzazioni. Il filmato di laboratorio non contiene una consulenza assicurativa.

## Riprodurre il test host
Servono Python 3.12, compilatore C99, CMake, FFmpeg/ffprobe con libass e encoder H.264/AAC, circa 2 GB liberi per tool e modelli host. Per la cross-compilazione di entrambi i target occorre ulteriore spazio per SDK e NDK. Eseguire i comandi dalla radice del repository. Consultare asr/ e llm/ per gli strumenti dei singoli modelli.

```sh
python3 asr/bootstrap.py runtime/asr
python3 llm/bootstrap.py --help
```

Una volta disponibili i due eseguibili e i due modelli nel profilo locale, impostare ASR_CLI, ASR_MODEL, LLM_CLI, LLM_MODEL con percorsi assoluti e avviare:

```sh
sh tests/host-spike.sh
```

Il comando crea una directory nuova in build/ e stampa il percorso del job. Rilegge il WAV tramite Whisper, seleziona una parola del parlato con Qwen, crea il flusso RGBA del contratto, compone e codifica il master, verifica stream e audio e confronta le parole con il riferimento. In caso di discordanza del testo lo stato è BLOCKED. I file già prodotti servono alla diagnosi. L'harness Python è uno strumento per il computer di collaudo.

## Diagnosi
- **Hash del modello diverso dal lock.** Verificare download, revisione e quantizzazione con sha256sum o Get-FileHash. Il modello multilingue small Q5_1 ha trascritto correttamente il campione italiano disponibile. Vedere qa/asr per il risultato di tiny sullo stesso campione.
- **Nessun codec disponibile.** Rilevare codec e ABI del dispositivo. Annotare errore, modello hardware e file coinvolto. Provare un input compatibile mantenendo il master a 1080 × 1920. Una prova fallita lascia aperta la verifica di compatibilità.
- **Voce o didascalia errata.** Confrontare result.srt con l'audio e lo script approvato. Sul banco host è possibile correggere l'SRT e ripetere scripts/master.py con --srt in una nuova destinazione. Conservare sia l'output automatico sia la correzione.
- **Cambio firma Android.** Il gestore pacchetti richiede la stessa firma per aggiornare una installazione esistente. Salvare prima i file necessari. Usare sempre lo stesso keystore del proprietario nei test di aggiornamento. I build di test con certificato effimero vanno collaudati in un profilo separato.
- **Spazio o interruzioni.** Conservare gli ingressi originali e l'ultimo export valido. Sul banco host scegliere un nuovo job per ripetere il test. La ripresa automatica completa è un requisito aperto.

Per il collaudo fisico usare docs/COLLAUDO.md. Il manuale del prodotto completo e l'approvazione della release dipendono dalle milestone ancora aperte.
