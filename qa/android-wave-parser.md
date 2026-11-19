# Verifica parser WAV Android su JVM host
Eseguiti 24 casi con JDK 21 e target bytecode Java 8. Comando dalla radice del repository, con JAVA_HOME configurato sul JDK disponibile:

```sh
bash tests/android-wave/run.sh
```

Esito PASS. Il log è android-wave-unit.txt. Le fixture vengono costruite in memoria dal test e scritte in file temporanei eliminati a fine controllo.

WavPcm controlla firma RIFF/WAVE, corrispondenza della dimensione dichiarata all'intero file, integrità di intestazioni/payload/padding, unicità dei chunk fmt e data, numero di canali, formato PCM16, frequenze ammesse, allineamento frame e byte rate. Gestisce chunk non riconosciuti e data prima di fmt, verificando i metadati prima dell'uso. I conteggi e gli offset sono long, la dimensione unsigned del chunk è confrontata per sottrazione prima del calcolo dell'offset. La durata massima rimane dieci minuti.

Wav.encode legge ora i metadati tramite questo parser e conserva il percorso di codifica AAC streaming esistente. La lettura del contenitore non carica il PCM intero in memoria.

La politica del parser rifiuta RIFF con dati oltre la dimensione dichiarata, chunk fmt/data duplicati e padding incompleto. PCM24, float, WAVE extensible, RF64 e frequenze diverse da 16/48 kHz rimangono formati non supportati per questa prova. L'estensione fmt PCM è accettata solo quando la lunghezza cbSize coincide con quella dichiarata.

Il collaudo MediaCodec, il muxing su hardware ARM64 e il confronto audio restano NON ESEGUITI in questa verifica JVM. La compilazione APK e il collaudo fisico sono attività separate.
