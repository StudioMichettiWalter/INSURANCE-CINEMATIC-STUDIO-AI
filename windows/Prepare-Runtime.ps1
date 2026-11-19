[CmdletBinding()]
param([string]$Directory = (Join-Path $env:LOCALAPPDATA 'InsuranceStudioM0-Runtime'),
      [string]$WhisperModel, [string]$QwenModel)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$runtime = [IO.Path]::GetFullPath($Directory)
[void][IO.Directory]::CreateDirectory($runtime)
$asr = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'asr.lock.json')) | ConvertFrom-Json
$llm = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'dependencies.lock.json')) | ConvertFrom-Json
$ff = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'windows.lock.json')) | ConvertFrom-Json
function Download([object]$entry, [string]$name, [string]$existing) {
    if ($existing) {
        $path = (Resolve-Path -LiteralPath $existing).ProviderPath
        if ((Get-FileHash -LiteralPath $path).Hash -ine $entry.sha256) { throw "SHA-256 inatteso: $path" }
        return $path
    }
    $path = Join-Path $runtime $name
    if (!(Test-Path -LiteralPath $path)) {
        $temporary = Join-Path $runtime ([Guid]::NewGuid().ToString()+'.partial')
        try {
            Write-Host "Download $name"
            Invoke-WebRequest -UseBasicParsing -Uri $entry.url -OutFile $temporary
            if ((Get-FileHash -LiteralPath $temporary).Hash -ine $entry.sha256) { throw "SHA-256 inatteso: $name" }
            [IO.File]::Move($temporary,$path)
        } finally { if ([IO.File]::Exists($temporary)) { [IO.File]::Delete($temporary) } }
    } elseif ((Get-FileHash -LiteralPath $path).Hash -ine $entry.sha256) { throw "File gia presente diverso dal lock: $path" }
    return $path
}
function Unpack([object]$entry, [string]$name, [string]$exe) {
    $archive = Download $entry "$name.zip" ''
    $folder = Join-Path $runtime ($name+'-'+[Guid]::NewGuid().ToString('N'))
    Expand-Archive -LiteralPath $archive -DestinationPath $folder
    $matchesFound = @(Get-ChildItem -LiteralPath $folder -Recurse -File -Filter $exe)
    if ($matchesFound.Count -ne 1) { throw "Archivio non riconosciuto: $name" }
    return $matchesFound[0].FullName
}
# Explicit installer invocation only. Export itself performs no network operation.
$assets = [ordered]@{}
$assets.WhisperModel = Download $asr.model 'whisper-small-q5_1.bin' $WhisperModel
$assets.QwenModel = Download $llm.model 'qwen2.5-q4.gguf' $QwenModel
$assets.Whisper = Unpack $asr.windows_cli 'whisper-v1.7.6' 'whisper-cli.exe'
$assets.Llama = Unpack $llm.windows 'llama-b6000' 'llama-cli.exe'
$assets.Ffmpeg = Unpack $ff.ffmpeg 'ffmpeg-essentials-9.0.2' 'ffmpeg.exe'
$assets.Ffprobe = Join-Path ([IO.Path]::GetDirectoryName($assets.Ffmpeg)) 'ffprobe.exe'
if (![IO.File]::Exists($assets.Ffprobe)) { throw 'ffprobe mancante nel pacchetto.' }
$config = Join-Path $runtime 'runtime.json'
$temp = Join-Path $runtime ([Guid]::NewGuid().ToString()+'.json')
[IO.File]::WriteAllText($temp, ($assets | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
if ([IO.File]::Exists($config)) { [IO.File]::Replace($temp,$config,$null) } else { [IO.File]::Move($temp,$config) }
Write-Output $config
