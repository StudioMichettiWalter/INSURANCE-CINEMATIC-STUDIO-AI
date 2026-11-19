param([Parameter(Mandatory=$true)][string]$Ffmpeg, [Parameter(Mandatory=$true)][string]$WorkingDirectory,
      [Parameter(Mandatory=$true)][string]$AcceptedMaster)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $root 'Export-M0.ps1'),[ref]$null,[ref]$null)
$quote = $ast.Find({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Quote-Arg'},$true)
Invoke-Expression $quote.Extent.Text
. (Join-Path $root 'Quality.ps1')
[void][IO.Directory]::CreateDirectory($WorkingDirectory)
$valid = Measure-Export $Ffmpeg $AcceptedMaster
if ($valid.status -ne 'QA') { throw 'Il master di riferimento deve superare i controlli.' }
$black = Join-Path $WorkingDirectory 'black.mp4'
& $Ffmpeg -nostdin -loglevel error -n -f lavfi -i 'color=c=black:s=160x160:r=30:d=1' -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=1' -af 'loudnorm=I=-16:TP=-2' -c:v libx264 -threads 1 -pix_fmt yuv420p -c:a aac -ar 48000 $black
if ($LASTEXITCODE) { throw 'Creazione fixture nera fallita.' }
$bad = Measure-Export $Ffmpeg $black
if ($bad.status -ne 'BLOCKED' -or $bad.checks.nessun_frame_nero) { throw 'Fotogrammi neri non bloccati.' }
$silent = Join-Path $WorkingDirectory 'silent.wav'
& $Ffmpeg -nostdin -loglevel error -n -f lavfi -i 'anullsrc=r=48000:cl=mono:d=1' $silent
if ($LASTEXITCODE) { throw 'Creazione fixture muta fallita.' }
$quiet = Measure-Export $Ffmpeg $silent
if ($quiet.status -ne 'BLOCKED' -or $quiet.checks.livelli_misurabili) { throw 'Silenzio non bloccato.' }
[ordered]@{ accepted=$valid; black=$bad; silence=$quiet } | ConvertTo-Json -Depth 8
