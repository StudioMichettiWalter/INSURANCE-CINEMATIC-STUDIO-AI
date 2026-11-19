# Registro modifiche
## 0.1.0-m0.1
Prima consegna tecnica associata alla specifica v1.0. Conservati il mandato e la skill originali. Introdotti il renderer C condiviso del contratto con pagine deformabili, i primi adattatori nativi, i test host di trascrizione italiana, inferenza strutturata locale e compositing verticale. Per le funzioni effettivamente collaudate consultare STATUS.md e qa/.

## 0.1.0-m0.3
Corretto il trattamento Android dei cue lunghi, introdotti import SRT corretto, export diagnostico ZIP e parser PCM RIFF verificato. Rafforzato il gate Windows sul file codificato, con normalizzazione in due passaggi e codifica AAC 192 kbit/s. Ricompilati i pacchetti di prova. Gate fisico M0 ancora aperto.

## 0.1.0-m0.4

- Archivio di lavorazione condiviso tra Windows e Android con clip, voce, cue SRT e controllo SHA-256.
- Rapporti di render con hash dell’archivio usato e kit per il confronto dei due export.
- Nuove build di prova e verifiche di scambio tra PowerShell e Java su host Linux.
