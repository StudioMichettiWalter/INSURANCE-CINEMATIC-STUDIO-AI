# Trascrizione italiana sul telefono

La libreria JNI collega lo stesso adattatore C++ in `asr/` a whisper.cpp 1.7.6. ARM64, CPU baseline ARMv8-A, pesi multilingual small Q5_1 caricati dallo storage privato. La versione M0 espone cue per segmento. Il modello occupa 190.085.487 byte su disco. Il picco di memoria e le prestazioni termiche Android richiedono misure su dispositivo.

## Costruire la libreria

Da Linux x64, con NDK r27d completo, CMake 3.31 e make. Impostare NDK_HOME sulla cartella NDK. Questi comandi scaricano sorgenti pubblici da un'origine fissa sul computer di sviluppo:

```sh
mkdir -p build/asr-deps
curl -fL --retry 2 https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/refs/tags/v1.7.6 -o build/asr-deps/src.tgz
echo '166140e9a6d8a36f787a2bd77f8f44dd64874f12dd8359ff7c1f4f9acb86202e  build/asr-deps/src.tgz' | sha256sum -c -
tar -xzf build/asr-deps/src.tgz -C build/asr-deps
export WHISPER_SRC="$PWD/build/asr-deps/whisper.cpp-1.7.6"
export ASR_BUILD_DIR="$PWD/build/asr-arm64"
export ASR_LIB_OUT="$PWD/build/arm64/libstudio_asr.so"
./inference-android/build.sh
export ANDROID_ASR_LIB="$ASR_LIB_OUT"
```

Collocare il modello specificato in `asr/asr.lock.json` in una cartella accessibile dal selettore Android. La schermata verifica byte e SHA-256 prima di aprirlo. Selezionare il modello, quindi la stessa narrazione che sarà usata nell'editor. L'ASR accetta file audio leggibili da MediaExtractor e MediaCodec del dispositivo con output PCM signed 16 bit da 16 a 192 kHz, fino a 8 canali e 10 minuti. Per collaudo usare WAV PCM o AAC-LC/M4A a 48 kHz. La copia di lavoro ha un limite di 512 MiB. Formati variabili o PCM incompatibile producono un errore. L'audio viene ridotto a mono e integrato a 16 kHz prima della trascrizione italiana locale. La narrazione master resta integra.

La trascrizione riuscita viene salvata in `files/asr-cues.json` con lingua, digest dell'audio e del modello, testo e intervalli in millisecondi. L'operazione avviene in un thread in background. Il pulsante Annulla interrompe lettura o inferenza; il modello in fase di caricamento può ritardare l'annullamento. Il cambio di Activity annulla il lavoro in corso. Il file di cue precedente sopravvive a un errore prima del commit atomico. Dopo un arresto di processo avviare di nuovo l'operazione. La ripresa a metà inferenza è aperta.

Le licenze MIT di whisper.cpp e dei pesi sono in `asr/licenses/`. Gli avvisi LLVM/libc++ del toolchain usato sono in `NDK-LLVM-NOTICE.txt`. Il decoder MediaCodec proviene dal sistema operativo. Nessun modello viene incorporato nell'APK. Il test fisico di importazione, decodifica, ASR, qualità degli intervalli, cancellazione, pressione di memoria e commit resta da eseguire. Il confronto sullo stesso parlato deve includere il riferimento umano.

