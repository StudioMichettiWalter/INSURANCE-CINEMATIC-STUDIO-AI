# Verifica del kit M0.3 su host Linux

- Kit ZIP integro. Clip H.264 360 × 640 a 30 fps, 150 fotogrammi. Voce sintetica PCM16 mono a 16 kHz, durata 3,287 s.
- Export PowerShell 7 con SRT fornito e in modalità automatica. Entrambi hanno prodotto uno ZIP integro e un master da 99 fotogrammi. Loudness −16,86 LUFS e true peak −1,35 dBTP in entrambi.
- Whisper ha trascritto «leggi le condizioni del contratto.» con un cue da 0 a 2,280 s. Qwen ha proposto «condizioni» come parola chiave e «leggi le» come titolo. Il titolo frammentario richiede revisione editoriale.
- Il confronto diagnostico ha accettato due copie dello stesso master host e ha rifiutato sia un rapporto con hash della voce errato sia un cue diverso dal riferimento. L'accettazione M0 è rimasta pendente. La suite del comparatore del bundle progetto ha superato quattro test.
- Il banco usa driver PowerShell con FFmpeg e renderer C compilati per Linux. Installazione, render, RAM e temperature su Windows 11 e Realme fisici restano NON ESEGUITI.
