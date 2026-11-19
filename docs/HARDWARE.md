# Hardware di collaudo del proprietario

Dati comunicati da Walter Michetti il 19 novembre 2026. Inventario dichiarato, esecuzione fisica ancora NON ESEGUITA.

| Dispositivo | Dato dichiarato | Da rilevare sul dispositivo |
| --- | --- | --- |
| Smartphone | Realme 11 Pro+ 5G, 12+12 GB | Versione Android e realme UI, ABI, RAM fisica e impostazione espansione, spazio libero, encoder AVC/AAC |
| PC | Windows 11 | Edizione/build, architettura x64, CPU, RAM, spazio libero e driver |

L'indicazione 12+12 rappresenta RAM e memoria espansa secondo la configurazione dichiarata. Registrare separatamente i due valori nel verbale. Il picco di memoria dell'app va misurato durante ASR, LLM e render.

## Prima prova
1. Scaricare il ramo insurance-studio-download dal repository INSURANCE-CINEMATIC-STUDIO-AI.
2. Su Windows usare Insurance-Studio-M0.3-Setup.exe e la preparazione runtime descritta nel manuale.
3. Sul Realme usare Studio-M0.3-r1-arm64-test.apk in un profilo di collaudo. La firma è effimera di test. Per conservare progetti e modelli di un'installazione precedente, salvare prima i file e pianificare la firma stabile.
4. Copiare sul telefono i due modelli ricomposti dal kit e una clip MP4 H.264 con voce WAV PCM16 mono 16 kHz. Per la fixture riproducibile seguire tests/host-spike.sh.
5. Verificare importazione, trascrizione, correzione/import SRT, selezione della parola chiave e render. Durante la prova autonoma spegnere il PC dopo il trasferimento dei file e scollegare la rete.
6. Salvare lo ZIP diagnostico Android e l'export Windows con report. Annotare errori, durata, RAM e stato termico secondo docs/COLLAUDO.md. I modelli richiedono memoria durante l'inferenza oltre alla loro dimensione su disco.

Esiti iniziali da compilare dopo osservazione effettiva.
- Installazione sul Realme NON ESEGUITO.
- Installazione sul PC Windows 11 NON ESEGUITO.
- Export Android autonomo NON ESEGUITO.
- RAM, temperature, crash e parità NON ESEGUITO.
