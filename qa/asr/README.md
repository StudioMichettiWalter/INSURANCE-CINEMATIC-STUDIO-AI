# Esito ASR host • 19 novembre 2026

Ingresso condiviso tests/asr/voce.wav, PCM mono a 16 kHz, 52.592 campioni. La frase è «Leggi le condizioni del contratto». Modello e decodifica sono locali alla macchina Linux x86_64. I log riportano CPU, caricamento del modello, tempi e buffer di lavoro.

| Controllo | Esito | Evidenza |
| --- | --- | --- |
| Tiny GGML, testo della fixture | FAIL | voce/raw.json e tiny-score.json. Sei operazioni di modifica su cinque parole. |
| Small Q5_1, testo della stessa fixture | PASS | accettata/raw.json e small-score.json. Tutte e cinque le parole riconosciute. |
| Timestamp segmento entro durata audio | PASS | accettata/result.json, 0–2280 ms su 3287 ms. |
| Emissione SRT | PASS | accettata/result.srt |
| Binding C, cancellazione prima del caricamento e inferenza dalla fixture PCM | PASS | native.log registra cue 0–2280 ms e codice 0 della prova tests/asr/native.cpp. native-sample.json riporta un campione Linux di VmHWM. |
| Collaudo ASR su Windows nativo e Android fisico | NON ESEGUITO | Richiesta prova sui due target. |

Nel collaudo non è stato passato un prompt con il testo atteso. I timestamp sono stime dell'ASR e richiedono ascolto comparativo. La percentuale di errore riguarda esclusivamente questa frase. L'uscita tiny resta nel report di regressione. Nessuna decisione assicurativa è stata presa sulla base del testo trascritto.
