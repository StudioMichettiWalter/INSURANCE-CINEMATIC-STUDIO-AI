[CmdletBinding()]
param([Parameter(Mandatory)][string]$Directory)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$lock = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'asr.lock.json') | ConvertFrom-Json
$base = [IO.Path]::GetFullPath($Directory)
[void][IO.Directory]::CreateDirectory($base)
function Get-VerifiedFile($url, $hash, $output) {
    if ((Test-Path -LiteralPath $output) -and (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash -eq $hash) { return }
    $part = Join-Path $base ([guid]::NewGuid().ToString() + '.part')
    try {
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $part
        if ((Get-FileHash -LiteralPath $part -Algorithm SHA256).Hash -ne $hash) { throw "SHA-256 inatteso per $output" }
        if (Test-Path -LiteralPath $output) { throw "File preesistente diverso dal lock. Scegliere una directory libera." }
        [IO.File]::Move($part, $output)
    } finally {
        if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part }
    }
}
$zip = Join-Path $base 'whisper-bin-x64.zip'
Get-VerifiedFile $lock.windows_cli.url $lock.windows_cli.sha256 $zip
Get-VerifiedFile $lock.model.url $lock.model.sha256 (Join-Path $base 'model.bin')
# Estrazione in cartella nuova per evitare di avviare DLL preesistenti.
$bin = Join-Path $base ('runtime-' + [guid]::NewGuid().ToString())
Expand-Archive -LiteralPath $zip -DestinationPath $bin
Write-Output (Join-Path $bin 'Release\whisper-cli.exe')
Write-Output (Join-Path $base 'model.bin')
