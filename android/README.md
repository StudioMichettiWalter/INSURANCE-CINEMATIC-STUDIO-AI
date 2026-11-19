# Laboratorio Android ARM64
La build corrente richiede Android API 27, target API 35 e ABI arm64-v8a. API 27 è un valore provvisorio per la compilazione NDK e l'uso delle API media. La verifica di installazione e delle capacità encoder sul dispositivo deve precedere la scelta definitiva di minSdk.

Installare Studio-M0-arm64-test.apk dal gestore file sul telefono di collaudo. La firma di test usa un certificato effimero valido 30 giorni. Per aggiornamenti riproducibili ricompilare con il keystore del proprietario, tenuto fuori dal repository.

Importare clip e voce dai pulsanti del laboratorio. Il selettore SAF copia i file nello storage privato con hash e deduplica. Importare i modelli .bin e .gguf del kit, trascrivere la voce e proporre la parola chiave. Il rendering legge i cue salvati e usa la stessa geometria C della versione Windows. Salvare l'MP4 attraverso il selettore di destinazione. Consultare gli errori sullo schermo se il decoder o encoder del dispositivo rifiuta un formato.

Per il primo test usare WAV PCM mono 16 kHz e clip MP4 H.264, con durata sufficiente a coprire la voce. Il formato master richiesto è 1080×1920, 30 fps, AVC e AAC 48 kHz. La conformità effettiva del file richiede collaudo su hardware e misura con ffprobe. Il mix Android e la resa dei sottotitoli richiedono confronto con gli altri target. L'app dispone di annullamento, mentre la ripresa completa dopo terminazione resta aperta.

## Ricostruzione
Da Linux x64 con curl, Python 3, CMake e make eseguire bash android/bootstrap.sh con una nuova directory di tool. Il bootstrap prepara strumenti ufficiali e compila le librerie ARM64. I download sono verificati contro hash fissati nello script e nei lockfile. In alternativa compilare ASR con inference-android/build.sh, LLM con llm/build-android.sh e usare android/build.sh con le variabili indicate nel file.

Per firmare con una chiave esistente impostare STUDIO_KEYSTORE sul percorso del file, STUDIO_ALIAS e STUDIO_PASSWORD nell'ambiente. Evitare password negli argomenti della shell o nei log. Il builder invoca apksigner e verifica il pacchetto risultante. I dati di test sono in tests/ e vengono mantenuti fuori dall'APK.
