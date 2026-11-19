# Prove dell’archivio M0.4

## Esecuzioni su host Linux

- `tests/android-project/host.py` prova scrittura PowerShell, lettura e riscrittura JVM, render Windows tramite PowerShell host, hash di archivio, clip, voce e master. I casi negativi coprono alterazione dell'SRT e un percorso ZIP che risale alla directory madre.
- Il kit, aperto con `Windows-Collaudo.ps1 -Progetto` sullo stesso banco, ha prodotto 99 fotogrammi a 30 fps, loudness −16,86 LUFS e true peak −1,35 dBTP. Il rapporto ha registrato l'hash esatto dell'archivio di ingresso.
- Il comparatore ha accettato due copie dello stesso master host e ha rifiutato una fixture con un hash dell’archivio diverso. L’accettazione M0 è rimasta pendente in entrambi i casi.

## Build

La build Android ha prodotto APK con codice versione 4, ABI arm64-v8a, minSdk 27 provvisorio e target 35. La verifica apksigner ha confermato la firma di prova. LLVM-MinGW ha costruito renderer e launcher x64, e NSIS ha generato il setup Windows.

## Prove fisiche

Installazione, esecuzione Android autonoma, misure termiche, memoria, aggiornamento con firma del proprietario e parità nativa restano NON ESEGUITI. Il confronto del kit usa una composizione media di tre ingressi e non copre il progetto con timeline multitraccia.
