# Confronto tecnico di due export

Il programma verifica due esportazioni ricondotte allo stesso bundle. Si esegue dopo aver recuperato i file dalle due piattaforme. L'origine di ciascun export va documentata nel verbale di collaudo con modello del dispositivo, sistema, build, encoder, versione dei modelli e log locale. La copia e l'esecuzione indipendente su dispositivo fisico restano controlli umani.

## Raccolta e comando

1. Conservare il bundle ZIP d'ingresso originale. Importare lo stesso file su Windows e sul telefono. Impostare lo stesso seed su entrambi e completare i render. Registrare la provenienza dei risultati.
2. Raccogliere in due cartelle separate i seguenti file di ogni esecuzione: `master.mp4`, `captions.srt`, `project_manifest.json` e `render.json`. Il manifest deve essere la revisione del bundle effettivamente usata. `render.json` è un registro tecnico del piano realmente applicato. Deve contenere `bundle_sha256`, `seed` intero, `frames` e `scenes` nell'ordine della traccia principale. Ogni scena include `id`, `start_frame`, `end_frame` esclusivo e il payload dichiarativo effettivamente risolto (mesh, testi, parametri e keyframe secondo le primitive disponibili). Il confronto considera ogni campo JSON del registro, indipendentemente dall'ordine delle chiavi. Conservare dati variabili come timestamp di esecuzione e identificatori locali in un file distinto.
3. Usare Python 3.12 con NumPy e i comandi FFmpeg/ffprobe con filtro loudnorm. La suite di test importa anche le dipendenze del modulo dati. Da questa cartella:

```shell
python -m pip install -r requirements-host.lock -r ../data/requirements-host.lock
python -m unittest discover -s tests -v
python compare.py export-windows export-android progetto.studio.zip > confronto.json
```

Il risultato ha codice di uscita 0 per PASS e 1 per FAIL. File mancanti, timeline discontinua, profilo incompatibile o errori di decodifica producono un esito negativo. Un registro scritto a mano compromette la tracciabilità. La raccolta automatica di tutti i campi sulle due piattaforme è ancora aperta.

## Soglie proposte da approvare prima del collaudo di accettazione

| Controllo | Regola | Motivo |
| --- | --- | --- |
| Identità e contenuti | SHA-256 del bundle calcolato sul file fornito. Manifest JSON semanticamente uguale a quello del bundle, piano/seed uguali sui due target, testo e timestamp SRT coincidenti | Differenze anche minime di tagli, clausole o testo richiedono revisione |
| Video | H.264, YUV 4:2:0 a 8 bit, 1080×1920, conteggio esatto, PTS da zero a 30 fps con arrotondamento massimo di mezzo tick | Gli encoder possono produrre bitstream diversi conservando la stessa sequenza |
| Durata | Video entro un tick dal numero di frame / 30. Traccia AAC 48 kHz entro un frame video dalla durata prevista e dall'origine | Consente la granularità dei pacchetti audio |
| Campionamento visivo | Primo, centro e ultimo frame di ciascuna scena, più 9 posizioni uniformi; fino a 64 campioni equidistanti nella lista risultante. Media assoluta RGB per frame ≤3 su 255, massimo sulle tessere 30×30 ≤12 su 255 dopo riduzione area a 270×480 | Contiene la sensibilità all'encoder e rileva alterazioni localizzate di geometria o colore |
| Missaggio | Loudness integrato da −17 a −13 LUFS, differenza ≤0,5 LU e true peak ≤−1 dBTP in ogni file | Fascia attorno ai target narrativi −16/−14, con margine per differenze dell'encoder |
| Forma d'onda | Stesso numero e disposizione di canali; correlazione ≥0,985 su tracce mono a 8 kHz, ricerca ritardo entro ±8 campioni (1 ms) | Individua sostituzioni della voce e scarti temporali lasciando margine per la codifica AAC |

I valori sono ipotesi di lavoro. Calibrarli su più coppie di export dei dispositivi reali, annotare e approvare la revisione del criterio prima della verifica finale. Il confronto audio ridotto può perdere differenze stereo o ad alta frequenza. La riduzione dell'immagine e il campionamento possono perdere errori piccoli o tra campioni. Il confronto dei cue verifica i file SRT; resa dei glifi, leggibilità, colore della pelle, sincronismo percepito, rumori e correttezza assicurativa richiedono revisione umana sulle due riproduzioni. Nel laboratorio le fixture rimangono nelle cartelle temporanee del test.

## Diagnostica sui pacchetti M0.3

Il [kit guidato](../docs/COLLAUDO-RAPIDO.md) usa i due ZIP esportabili dalle build M0.3 e controlla gli hash e i cue della fixture prima del confronto tecnico. Il comando `compare_m0.py` condivide le misure audio e video di `compare.py`. Le applicazioni M0.3 esportano la diagnostica media. La registrazione dello stesso bundle progetto e del piano di render sui due target resta da completare per l'accettazione integrale.
