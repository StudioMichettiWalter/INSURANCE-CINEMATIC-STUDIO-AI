ADR 001 • Laboratorio M0. Separare renderer, inferenza locale e collaudo media. Un risultato Linux è una misura di sviluppo. Il gate Windows x64 e Android ARM64 richiede esecuzione nativa con gli stessi ingressi. Nessuna scelta definitiva su minSdk o motore UI prima delle misure.

ADR 002 • Scelte provvisorie dello spike. Il banco M0 usa C99 per un rasterizzatore 3D CPU comune e adattatori Win32/PowerShell e Java/JNI. Questo permette una prova concreta con le toolchain disponibili. Flutter/Rust, SQLite incorporato nelle app, Vulkan e renderer evoluto richiedono ancora valutazione e integrazione. Il fallback CPU produce geometria 3D con camera, pagina deformabile e ombre. La scena attuale è programmatica, il grafo dichiarativo completo resta aperto.

ADR 003 • Inferenza offline. Whisper.cpp v1.7.6 small multilingual Q5_1 per ASR e llama.cpp b6000 con Qwen2.5 0.5B Q4_K_M per selezione estrattiva strutturata. Tiny ha fallito la fixture italiana, small ha riconosciuto le cinque parole di riferimento. Questo campione non stabilisce accuratezza generale. I due modelli vengono forniti separati dai pacchetti app. Nessun provider remoto è configurato.

ADR 004 • Firma e compatibilità. Android minSdk 27 provvisorio, ABI ARM64 e target 35. Il pacchetto M0 usa firma di test effimera. Setup Windows per utente e senza Authenticode. L'installazione reale e la firma del proprietario sono prerequisiti per M9.

ADR 005 • Manifest. Il pacchetto originale contiene due documenti e nessun project_manifest.json. Il template v1 in data/ è un nuovo artefatto di sviluppo con schema e migrazione, non un documento recuperato dall'archivio.

ADR 006 • Cue e correzione manuale M0.3. Il rendering Android mantiene i timestamp di segmento ASR. Un segmento oltre due righe viene bloccato per correzione con SRT importato dall'utente. Lo ZIP diagnostico conserva SRT effettivamente applicato e hash degli ingressi. Le misure loudness Android restano esplicitamente aperte.

## Archivio media M0.4

Il trasporto per una singola composizione usa ZIP con quattro nomi fissi. Il manifest ASCII `studio-render.txt` porta il marcatore `INSURANCE-STUDIO-RENDER/1` e gli SHA‑256 di `clip.mp4`, `voce.audio` e `captions.srt` in quest’ordine. La lettura estrae solo i nomi ammessi in una directory nuova. I limiti decodificati sono 768 MiB per la clip, 128 MiB per la voce, 1 MiB per i cue e 512 byte per il manifest. Lo ZIP è limitato a 1 GiB. La revisione di progetto con storyboard, risorse e approvazioni continua a seguire lo schema in data/.
