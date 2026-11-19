# Renderer del contratto • M0

Il punto d'ingresso in scene.h scrive un fotogramma RGBA8 a partire dall'indice intero e dalla lunghezza della scena. Supporta proporzioni verticali, quadrate e orizzontali fino a 4096 pixel per lato e 8.388.608 pixel totali. I limiti proteggono i calcoli delle dimensioni. Il chiamante deve fornire almeno larghezza × altezza × 4 byte. La memoria di lavoro del rasterizzatore è un buffer di profondità float per pixel, rilasciato a fine chiamata. Si può interrompere la sequenza tra due fotogrammi. Il renderer non legge file, non usa rete, orologio o stato globale mutabile.

Il contratto comprende copertina incernierata, blocco di carta e pagina con 24 segmenti. L'integrazione della tangente curva conserva la lunghezza del foglio durante la voltata. Proiezione prospettica, interpolazione prospetticamente corretta, test di profondità e ombra direzionale sul piano sono calcolati sulla CPU. La texture parametrica distingue testo stilizzato e segno evidenziato. Le righe sono grafica astratta senza contenuto contrattuale. I pixel sullo sfondo hanno alpha zero, quelli dell'ombra hanno alpha 95. La geometria non richiede asset o servizi esterni. Il renderer non contiene sottotitoli o inferenza. I colori della mesh sono sRGB. L'aliasing ai bordi e le ombre nette sono limiti osservabili del prototipo.

## Ripetizione locale

Dalla radice del repository su una macchina con compilatore C99 e libreria matematica:

```sh
cc -std=c99 -O2 core/render.c core/scene.c -lm -o render
cc -std=c99 -O2 tests/renderer/test.c core/scene.c -lm -o test-render
./test-render
./render 360 640 60 | ffmpeg -f rawvideo -pixel_format rgba -video_size 360x640 -framerate 30 -i - -vf 'select=eq(n\,30)' -frames:v 1 contratto.png
```

Su Windows il programma imposta stdout in modalità binaria; per una pipeline di byte usare cmd.exe oppure una shell che conserva byte binari. L'API usa il chiamante come proprietario del buffer e codici d'errore espliciti. Le funzioni trigonometriche dipendono dalla libm della piattaforma. Il confronto bit per bit nel test copre la stessa build e macchina. La scena corrente non usa numeri casuali. La verifica su entrambi i target è aperta.
