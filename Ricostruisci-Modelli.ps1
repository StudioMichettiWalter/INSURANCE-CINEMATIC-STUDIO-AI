$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$dest = Join-Path $PSScriptRoot 'Modelli-locali-M0.zip'
if (Test-Path -LiteralPath $dest) { throw 'Il file ZIP esiste gia. Usare una cartella nuova.' }
$parts = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter 'Modelli-locali-M0.zip.part*' | Sort-Object Name)
if ($parts.Count -ne 9) { throw 'Servono tutte le 9 parti. Scaricare lo ZIP completo del ramo GitHub.' }
$stream = [IO.File]::Open($dest, [IO.FileMode]::CreateNew)
try {
    foreach ($part in $parts) {
        $inputStream = [IO.File]::OpenRead($part.FullName)
        try { $inputStream.CopyTo($stream) } finally { $inputStream.Dispose() }
    }
} finally { $stream.Dispose() }
$expected = '7224744d9a9a1d57272cfd2a1137a43cf38c283c00bcccd7184f0bcf7e9c4a99'
if ((Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'SHA-256 errato. Non estrarre il file. Scaricare nuovamente tutte le parti.'
}
Write-Host 'Modelli ricomposti e hash verificato. Estrarre Modelli-locali-M0.zip.'
