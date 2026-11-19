# Prova renderer condiviso su host Linux x86_64

| Prova | Esito | Osservazione |
|---|---|---|
| GCC C99, -O2 -Wall -Wextra -pedantic -Werror | PASS | Collegamento CLI con -lm |
| tests/renderer/test.c | PASS | Bounds, singolo frame, buffer ripetibile, trasformazione, pixel di ombra e di geometria |
| Stesso test con AddressSanitizer e UndefinedBehaviorSanitizer | PASS | Uscita 0 |
| Stream grezzo, 90 frame, 1080 × 1920 | PASS | 2,270 s wall clock, max RSS del processo figlio 16.916 KiB con GCC -O2; stdout scartato |
| Decodifica FFmpeg di tre frame 256 × 320 | PASS | Controllo visivo sul frame centrale: pagina sollevata, copertina aperta, ombra e sfondo trasparente |
| Parità Windows / Android fisico | NON ESEGUITO | Queste misure si riferiscono unicamente all'host Linux |

Il tempo comprende rasterizzazione e scrittura su /dev/null. Esclude compositing, codifica MP4 e inferenza. Comando di misura: `/tmp/studio-render 1080 1920 90` con stdout su /dev/null. Cronometro monotono e getrusage(RUSAGE_CHILDREN) nel processo padre Python appena avviato.
