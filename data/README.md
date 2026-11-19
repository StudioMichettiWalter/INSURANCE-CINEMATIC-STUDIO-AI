# Progetti e backup del laboratorio M0

Il file `project_manifest.json` è un nuovo esempio strutturale vuoto, creato per la specifica v1. L'archivio originale cita un template ma contiene soltanto il mandato e la skill. Il comando `new` assegna ogni volta un UUID v4. Conserva la skill originale in `../source/insurance-cinematic-studio/SKILL.md`.

## Uso su host

Da questa cartella, con Python 3.12, SQLite e dipendenze nel lockfile, eseguire su un filesystem locale con supporto agli hard link.

```shell
python -m pip install -r requirements-host.lock
python -m unittest discover -s tests -v
python studio_project.py new progetto "Primo progetto"
python studio_project.py ingest progetto percorso/voce.wav
python studio_project.py show progetto > bozza.json
```

Modificare il titolo, il testo o le scene nella bozza. Conservare UUID, numero revisione e digest del genitore letti da `show`. Salvare il file in UTF-8 senza BOM. Usare Python per scrivere in UTF-8 nelle versioni Windows PowerShell che redirigono in UTF-16.

```shell
python studio_project.py commit progetto bozza.json
python studio_project.py backup progetto backup.studio.zip
python studio_project.py restore backup.studio.zip copia-recuperata
python studio_project.py show copia-recuperata
```

Il codice Python è un editor di riferimento e un collaudatore host. Il formato JSON/SQLite/ZIP è indipendente dal linguaggio. L'integrazione nel runtime Windows e Android, i picker, la sincronizzazione, la firma delle revisioni, la gestione permessi SAF e il collaudo fisico restano aperti.

## Formato e gestione degli errori

* Manifest conforme a JSON Schema 2020-12. Campi e stati ammessi sono chiusi. Gli ID di scene e asset sono UUID. Gli intervalli fotogrammi sono semiaperti a 30 fps. I prompt fanno riferimento al relativo ID scena.
* SQLite registra l'intero JSON canonico UTF-8 per ciascuna revisione e il suo SHA-256. La revisione successiva indica il digest della precedente. Le scritture usano una transazione con blocco immediato e rifiutano una bozza obsoleta. Riaprire il manifest e riapplicare l'intervento in caso di conflitto. La prima inizializzazione applica lo schema SQL in una transazione. Versioni sconosciute bloccano l'apertura.
* Gli asset sono copiati a blocchi, indicizzati col digest e immutabili. Il nome originale resta metadato visualizzabile. Un'interruzione prima del commit può lasciare un file orfano nella cache. I backup includono solo oggetti referenziati dalla cronologia.
* Il backup legge uno snapshot coerente e salva un manifest corrente, le revisioni e tutti gli asset storici. Verifica i digest durante la copia. L'archivio si pubblica tramite hard link locale con nome esclusivo dopo chiusura. Usare un nuovo nome se esiste già.
* Il ripristino accetta esclusivamente nomi interni predefiniti, entry uniche, file regolari e compressioni stored/deflate. Verifica dimensioni, CRC dei membri letti, catena, identità del progetto, puntatore corrente e asset. Estrae nella directory temporanea sorella e installa dopo il commit in una nuova destinazione. I limiti del laboratorio sono 8 GiB complessivi espansi, 8192 membri e 2 MiB per ciascun manifest. Il modello di minaccia copre archivi ricevuti, con directory locale di lavoro controllata dall'utente. In assenza di firma un digest non identifica l'autore di un bundle.
* Il campo di stato è una proprietà dati. Il comando `commit` verifica struttura e riferimenti. L'autorizzazione al rilascio, il blocco per QA materiale e le approvazioni sono responsabilità del futuro orchestratore. Nessuna automazione di pubblicazione.
* La suite copre anche corruzione, rollback del ripristino, link simbolici, percorso non ammesso, voci duplicate e revisione obsoleta. Backup e ripristino sono stati provati su Linux. Disco pieno fisico, interruzioni di alimentazione e filesystem mobili sono ancora da provare.
