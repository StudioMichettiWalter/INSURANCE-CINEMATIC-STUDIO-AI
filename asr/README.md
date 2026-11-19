# Trascrizione italiana locale • spike M0

La libreria riceve campioni mono normalizzati a 16 kHz e restituisce cue per frase con tempi in millisecondi. La prova host usa anche la CLI upstream con lo stesso modello. Il modello accettato per questa fixture è Whisper small multilingue Q5_1, 190.085.487 byte. Tiny, provato sullo stesso audio, ha generato un errore materiale. Non approvare automaticamente il testo, i numeri o le clausole in base a questo collaudo.

## Ripetere su Linux di sviluppo

Prerequisiti di build verificati nella sessione: CMake 3.31.6, g++ 14, GNU Make, Python 3.12, ffmpeg con decodifica dell'audio in ingresso. Il primo comando accede alla rete per i soli pesi e sorgenti indicati in asr.lock.json. I successivi lavorano localmente. Scegliere ogni volta una directory di risultati nuova.

```sh
python3 asr/bootstrap.py ../toolchain-asr
python3 asr/transcribe.py ../toolchain-asr/build/bin/whisper-cli ../toolchain-asr/model.bin tests/asr/voce.wav ../risultati-asr-01
python3 tests/asr/check.py ../risultati-asr-01/result.json
```

Per un materiale proprio sostituire tests/asr/voce.wav. Il controllo check.py confronta esclusivamente la fixture con la frase di riferimento. result.json riporta modello, lingua, durata e cue. result.srt contiene le didascalie. raw.json conserva l'output integrale dell'inferenza.

## Binding C per i target

Aggiungere asr con add_subdirectory e passare WHISPER_SOURCE_DIR (sorgenti estratti dal bootstrap). Linkare il target studio_asr. Il chiamante verifica hash dei pesi e converte il file selezionato in float mono 16 kHz nell'ordine temporale di presentazione. Chiamare studio_asr_italian fuori dal thread UI. Accumulare i cue in una revisione provvisoria e confermarli solo al codice di ritorno zero. Il puntatore al testo vale durante il callback. L'annullamento è cooperativo durante la decodifica. Questo spike limita l'input a 600 secondi e carica il modello per ciascuna chiamata. Android richiede sistemi di build e decodifica nativi, senza Python a runtime.

```sh
cmake -S asr -B ../build-asr-test -DWHISPER_SOURCE_DIR="$PWD/../toolchain-asr/whisper.cpp-1.7.6" -DSTUDIO_ASR_TEST=ON -DCMAKE_BUILD_TYPE=Release
cmake --build ../build-asr-test --parallel 2 --target studio_asr_test
ffmpeg -i tests/asr/voce.wav -f s16le ../toolchain-asr/test-pcm.bin
../build-asr-test/studio_asr_test ../toolchain-asr/model.bin ../toolchain-asr/test-pcm.bin
```

## Windows x64

In PowerShell avviare `asr\bootstrap-windows.ps1 -Directory .\strumenti-asr`. Il comando verifica SHA-256 dell'archivio ufficiale CPU e del modello e stampa i due percorsi. Conservare insieme exe e DLL estratte. Sul computer di sviluppo Linux sono stati controllati digest di release, archivio e presenza della CLI. Esecuzione su Windows da collaudare.

```powershell
& $whisperCli -m $modello -f .\tests\asr\voce.wav -l it -t 2 -ng -osrt -oj -of .\prova
```

## Provenienza e limiti

Il lock riporta URL immutabile dei pesi e digest dei sorgenti. La dichiarazione MIT di OpenAI copre codice e pesi. La conversione GGML è distribuita dal progetto whisper.cpp. I due avvisi sono in licenses/. L'archivio binario Windows è prelevato a parte dal progetto upstream. Verificare gli avvisi dei componenti transitivi prima di redistribuirlo. Modelli e toolchain non sono nel sorgente.

Gli unici dati vocali consegnati sono sintetici e confinati a tests/asr. La fixture proviene da eSpeak NG 1.52.0, voce it, 145 parole/minuto. Per rigenerarla in una directory test:

```sh
espeak-ng -v it -s 145 -f tests/asr/riferimento.txt -w synth.wav
ffmpeg -i synth.wav -af apad=pad_dur=0.6 -ar 16000 -ac 1 voce.wav
```

Hash della fixture consegnata 8a220c5750d4ad2807e1b5a7345b7d578cfe750af0e04634a0ea251b72acc196. Una sola frase sintetica verifica la filiera. Affidabilità su parlato del consulente, cue parola per parola, termiche e RAM dei dispositivi restano da misurare. La generazione vocale è circoscritta al collaudo.
