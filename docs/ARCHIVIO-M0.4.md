# Archivio portabile di lavorazione M0.4

Le build M0.4 aprono lo stesso ZIP con una clip, la voce e i sottotitoli già temporizzati. Il formato include un manifest di versione e tre SHA‑256. Entrambe le app verificano le dimensioni, i nomi delle voci e gli hash prima di renderizzare. Il rapporto di esportazione conserva lo SHA‑256 dello ZIP originale.

## Prima prova con lo stesso archivio

1. Scaricare il [kit M0.4](https://github.com/StudioMichettiWalter/INSURANCE-CINEMATIC-STUDIO-AI/blob/insurance-studio-download/Kit-Collaudo-M0.4.zip), usando **Download raw file**. Estrarre il kit sul PC.
2. Scaricare e installare le build M0.4 per [Windows 11 x64 e Realme ARM64](https://github.com/StudioMichettiWalter/INSURANCE-CINEMATIC-STUDIO-AI/tree/insurance-studio-download). L’APK è firmato con un certificato di prova diverso da quello M0.3. Salvare i dati di un’eventuale installazione prima di pianificare la sostituzione. Su Windows preparare il runtime con il comando indicato in ISTRUZIONI.md.
3. In PowerShell, dalla cartella estratta, eseguire `.\Windows-Collaudo.ps1 -Progetto`. Conservare `Esito-Windows-portabile-*.zip`.
4. Trasferire il solo `Progetto-Collaudo-M0.4.zip` dal kit al Realme. Una volta completato il trasferimento spegnere il PC e scollegare la rete del telefono per la prova autonoma. In Laboratorio M0 premere **Apri archivio portabile e renderizza**, selezionare il file, poi premere **Salva video, SRT e rapporto ZIP**.
5. Per il confronto usare Python 3.12, NumPy, FFmpeg e ffprobe. Dalla cartella estratta del kit, sostituire il nome dello ZIP Windows con quello effettivo ed eseguire:

   ```text
   python -m pip install -r requirements-host.lock
   python compare_m0.py Esito-Windows-portabile-XXXXXXXX.zip Studio-M0-export.zip Kit-Collaudo-M0.4.zip
   ```

   Il confronto richiede che i due rapporti registrino l’hash del progetto distribuito nel kit. Verificare a parte inferenza, RAM, temperatura e resa percettiva con il protocollo completo.

## Creare un archivio

Usare clip MP4 e voce WAV PCM16 a 16 o 48 kHz. La clip deve coprire la voce, con un limite comune di prova pari a 300 s. I sottotitoli SRT devono rientrare nella durata della voce e nella safe area. Gli archivi possono essere trasferiti offline in entrambe le direzioni.

Su Windows, avviare Studio dal menu Start. Alla domanda sull’archivio portabile scegliere **No**, selezionare clip e voce, poi scegliere **No** alla domanda sulla trascrizione automatica per fornire un SRT. Scegliere **Sì** quando viene proposto il salvataggio dell’archivio dopo l’export del master.

Su Android, importare clip e voce, poi trascrivere la voce o importare un SRT corretto. Premere **Salva archivio portabile**. Il pulsante usa i cue già temporizzati associati alla voce.

Lo ZIP di lavorazione M0.4 copre una singola composizione con i tre ingressi e il renderer M0. La struttura del progetto completo, con storyboard, timeline, approvazioni, revisioni e asset multipli, resta una tappa successiva. I risultati del confronto del kit sono diagnostici e mantengono aperta l’accettazione M0.
