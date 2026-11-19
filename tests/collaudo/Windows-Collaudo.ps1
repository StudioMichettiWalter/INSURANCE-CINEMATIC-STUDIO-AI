[CmdletBinding()]
param([string]$StudioDirectory = (Join-Path $env:LOCALAPPDATA 'InsuranceCinematicStudioM0'),
      [string]$RuntimeConfig, [switch]$Automatico, [switch]$Progetto)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$fixture = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'fixture.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($name in @('clip.mp4','voce.wav','riferimento.srt')) {
    $path = Join-Path $PSScriptRoot $name
    $expected = $fixture.files.PSObject.Properties[$name].Value
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $expected) {
        throw "Il file $name non corrisponde al kit. Estrarre nuovamente lo ZIP."
    }
}
$export = Join-Path $StudioDirectory 'Export-M0.ps1'
if (!(Test-Path -LiteralPath $export -PathType Leaf)) {
    throw 'Installare Insurance-Studio-M0.3-Setup.exe prima del collaudo.'
}

if ($Automatico -and $Progetto) { throw 'Scegliere una sola modalita di prova.' }
$mode = if ($Progetto) { 'portabile' } elseif ($Automatico) { 'automatico' } else { 'srt' }
$id = [Guid]::NewGuid().ToString('N').Substring(0,8)
$folder = Join-Path $PSScriptRoot ("Esito-Windows-$mode-$id")
[void](New-Item -ItemType Directory -Path $folder)
$master = Join-Path $folder 'master.mp4'
$arguments = @('-Destinazione', $master)
if ($Progetto) {
    $archive = Join-Path $PSScriptRoot 'Progetto-Collaudo-M0.4.zip'
    if ($fixture.project_file -cne 'Progetto-Collaudo-M0.4.zip' -or
        (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $fixture.project_sha256) {
        throw 'Archivio portabile diverso dal kit.'
    }
    $arguments += @('-Progetto',$archive)
} else {
    $arguments += @('-Clip', (Join-Path $PSScriptRoot 'clip.mp4'), '-Voce', (Join-Path $PSScriptRoot 'voce.wav'))
    if (!$Automatico) { $arguments += @('-Sottotitoli', (Join-Path $PSScriptRoot 'riferimento.srt')) }
}
if ($RuntimeConfig) { $arguments += @('-RuntimeConfig', (Resolve-Path -LiteralPath $RuntimeConfig).ProviderPath) }
$powerShell = (Get-Process -Id $PID).Path
$timer = [Diagnostics.Stopwatch]::StartNew()
& $powerShell -NoProfile -File $export @arguments
$exportExit = $LASTEXITCODE
$timer.Stop()
if ($exportExit -ne 0) { throw "Export interrotto. Conservare il messaggio e i file in $folder" }

$report = @(Get-ChildItem -LiteralPath $folder -Directory -Filter 'master.mp4.*')
if ($report.Count -ne 1) { throw "Cartella rapporto Windows inattesa in $folder" }
Copy-Item -LiteralPath (Join-Path $report[0].FullName 'sottotitoli.srt') -Destination (Join-Path $folder 'captions.srt')
$reportFiles = @('qa.json','ffprobe.json')
$inference = Join-Path $report[0].FullName 'inferenza.json'
if (Test-Path -LiteralPath $inference -PathType Leaf) { $reportFiles += 'inferenza.json' }
foreach ($name in $reportFiles) {
    Copy-Item -LiteralPath (Join-Path $report[0].FullName $name) -Destination (Join-Path $folder $name)
}
$evidence = [ordered]@{
    schema_version = 1
    fixture = $fixture.id
    mode = $mode
    os = [Environment]::OSVersion.VersionString
    architecture = $env:PROCESSOR_ARCHITECTURE
    powershell = $PSVersionTable.PSVersion.ToString()
    elapsed_seconds = [Math]::Round($timer.Elapsed.TotalSeconds, 2)
    clip_sha256 = $fixture.files.'clip.mp4'
    voice_sha256 = $fixture.files.'voce.wav'
    master_sha256 = (Get-FileHash -LiteralPath $master -Algorithm SHA256).Hash.ToLowerInvariant()
}
[IO.File]::WriteAllText((Join-Path $folder 'prova.json'), ($evidence | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
$files = @('master.mp4','captions.srt','prova.json') + $reportFiles
$files = @($files | ForEach-Object { Join-Path $folder $_ })
Compress-Archive -LiteralPath $files -DestinationPath "$folder.zip"
Write-Host "Archivio per confronto: $folder.zip"
