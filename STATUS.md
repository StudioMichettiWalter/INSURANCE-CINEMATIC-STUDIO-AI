# Stato al 19 novembre 2026
Versione checkpoint 0.1.0-m0.1. M0 resta BLOCKED per accettazione multipiattaforma. Il laboratorio è compilabile, con pacchetti di prova effettivamente prodotti. M1–M9 restano aperte.

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
