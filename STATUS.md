# Stato al 19 novembre 2026
Versione corrente 0.1.0-m0.4. Le prime evidenze nella tabella si riferiscono alla baseline M0.1. M0 resta BLOCKED per accettazione multipiattaforma. Il laboratorio è compilabile, con pacchetti di prova effettivamente prodotti. M1–M9 restano aperte.

| Prova | Esito | Evidenza |
| --- | --- | --- |
| Integrità allegato e lettura skill | PASS | source/ conserva i documenti originali. Manifest assente nell'archivio |
| Renderer C, geometria 3D e pagina deformabile | PASS host Linux | qa/renderer-linux.md e qa/renderer-unit.txt |
| ASR italiano small Q5_1 | PASS sul campione | qa/asr/accettata, riferimento tests/asr |
| ASR tiny sullo stesso campione | FAIL | qa/asr/voce, tiny-score.json |
| Inferenza strutturata locale | PASS host Linux | qa/integrated/decision.json, qa/llm e qa/llm-unit.txt |
| Filiera clip + voce → ASR → LLM → 3D → MP4 | PASS tecnico host Linux | qa/integrated. 99 frame, 1080×1920, 30 fps CFR, AAC48k |
| SQLite, bundle, backup e difese percorsi | PASS host, 8 test | qa/data-unit.txt |
| Harness confronto export | PASS host, 4 test | qa/parity-unit.txt. Soglie proposte in parity/README.md |
| APK ARM64 con ASR e LLM JNI, firma test | PASS build | qa/android-final-build.log |
| Installer Windows x64 | PASS cross-build | qa/windows-final-build.log |
| Installazione Windows e Android fisico | NON ESEGUITO | Host Linux, telefono fisico assente |
| RAM/temperature/crash su target, PC spento | NON ESEGUITO | Procedura docs/COLLAUDO.md |
| Parità effettiva Windows/Android | NON ESEGUITO | Richiede export nativi dello stesso progetto |
| QA percettivo e assicurativo | NON ESEGUITO | Filmato tecnico sintetico nel laboratorio |
| Firma proprietario e release | NON ESEGUITO | Chiave e certificati del proprietario assenti |

## Requisiti ancora aperti
M0 richiede conferma hardware, formati e mix Android, verifica degli intervalli ASR e del sincronismo, minSdk motivato da prova fisica e confronto degli export. Le app dello spike non implementano ancora la UI completa, il DAG di 12 ruoli, la scena dichiarativa completa, le 30 animazioni, l'editor non distruttivo, integrazione SQLite, sync Drive e tutte le prove avversariali richieste. Il manuale è relativo al laboratorio. La SBOM fotografa i componenti disponibili e richiede revisione di distribuzione prima della release.

## Ripresa riproducibile
Dalla radice del repository eseguire python3 -m unittest discover -s data/tests -v. Per ricostruire Android con download verificati eseguire bash android/bootstrap.sh /percorso/nuovo/tool-m0. Su Windows predisporre il runtime come in windows/README.md. Poi seguire docs/COLLAUDO.md su hardware fisico, conservando i log e gli export immutabili.

Gli installer di prova e il kit modelli sono conservati nella cartella output del workspace. La cartella di consegna /codex/browser/... indicata dal client è risultata non creabile perché il filesystem radice è in sola lettura. I sorgenti e gli artefatti restano disponibili sotto /workspace.

## Checkpoint M0.3
Correzioni successive al checkpoint iniziale, mantenendo aperto il gate fisico.
- Android conserva gli intervalli ASR, blocca i cue oltre due righe o fuori dalla narrazione, accetta correzioni SRT con importazione locale e salva un archivio con MP4, SRT e rapporto diagnostico.
- Parser WAV estratto e verificato con 24 regressioni JVM. Parser SRT verificato con 22 casi, più 8 verifiche degli intervalli e della serializzazione dei cue.
- Build APK M0.3-r1 ARM64 PASS con firma effimera di test, qa/android-m0.3-build.log. Lo ZIP export e l'importazione SRT tramite SAF richiedono ancora prova Android fisica.
- Windows verifica loudness e true peak sul file AAC finale, oltre a frame neri e durate. Il vecchio singolo passaggio ha fallito sul campione (-17,41 LUFS e -0,52 dBTP). La correzione usa due passaggi e AAC 192 kbit/s. La nuova misura host è -16,86 LUFS e -1,35 dBTP. Le fixture silenziosa e con video nero vengono bloccate. Vedere qa/windows-new-quality.json.
- Installer M0.3 prodotto con cross-toolchain. Installazione ed esecuzione Windows native restano NON ESEGUITE.
- Misura loudness/true peak Android, confronto visivo e audio delle piattaforme, prova con PC spento, temperature e scelta definitiva di minSdk rimangono aperti. Questo checkpoint non supera M0.

I pacchetti precedenti restano riferimenti storici. L'APK M0.3-r1 ha un certificato di test diverso da quello precedente, usare un profilo di collaudo separato oppure salvare i dati prima di pianificare una nuova installazione. Il test di aggiornamento richiede il keystore stabile del proprietario.

Hardware dichiarato dal proprietario per il collaudo M0.3, Realme 11 Pro+ 5G 12+12 GB e PC Windows 11. Dettagli e prove ancora da rilevare in docs/HARDWARE.md. Nessuna compatibilità fisica dedotta dal solo nome commerciale.

## Kit per il collaudo fisico M0.3

Aggiunti clip, voce e SRT sintetici pronti all'uso, raccoglitore Windows e confronto diagnostico degli ZIP delle build correnti. I due percorsi Windows hanno prodotto la diagnostica attesa sul banco PowerShell Linux, con 99 fotogrammi e livelli audio nella soglia. La procedura per i dispositivi è in docs/COLLAUDO-RAPIDO.md. Evidenze host in qa/collaudo-kit-m03.md. Il gate fisico e la verifica dello stesso bundle progetto restano aperti.

## Checkpoint M0.4 — archivio portabile dei media

Windows e Android creano e aprono la stessa composizione ZIP di clip, voce e SRT, con verifica di manifest, dimensioni, nomi e SHA-256. Il rapporto registra lo SHA‑256 dello ZIP effettivamente renderizzato. Il kit M0.4 include un archivio sintetico per collaudare questo percorso e un comparatore che controlla la corrispondenza dell'archivio tra i rapporti.

- PASS su host Linux. PowerShell ha renderizzato un archivio riscritto da Java e Java ha verificato quello creato da PowerShell. La prova usa il motore C e FFmpeg Linux.
- PASS build APK ARM64 con firma di test e installer con eseguibili Windows x64. Gli hash sono distribuiti accanto ai pacchetti.
- NON ESEGUITO. Installazione fisica su Windows e Realme, esecuzione Android autonoma, RAM, temperatura, aggiornamento con firma del proprietario e parità degli export nativi.
- APERTO. Il formato M0.4 conserva i tre ingressi da renderizzare. Restano da completare il progetto editoriale revisionato nelle app, l'interfaccia completa, i dodici ruoli, le trenta animazioni, il montaggio multitraccia e la sincronizzazione Drive.

Prima prova riproducibile in docs/ARCHIVIO-M0.4.md. Evidenze tecniche in qa/archivio-m04.md.
