[CmdletBinding()]
param([string]$Clip, [string]$Voce, [string]$Sottotitoli, [string]$Destinazione,
      [string]$Whisper, [string]$ModelloAsr, [string]$Llama, [string]$ModelloLlm, [string]$RuntimeConfig)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$interactive = $PSBoundParameters.Count -eq 0
. (Join-Path $PSScriptRoot 'LocalAi.ps1')
. (Join-Path $PSScriptRoot 'Quality.ps1')

function Select-Path([string]$title, [string]$filter, [bool]$save) {
    $dialog = if ($save) { New-Object System.Windows.Forms.SaveFileDialog } else { New-Object System.Windows.Forms.OpenFileDialog }
    try {
        $dialog.Title = $title
        $dialog.Filter = $filter
        if ($dialog.ShowDialog() -ne 'OK') { throw 'Operazione annullata.' }
        return $dialog.FileName
    } finally { $dialog.Dispose() }
}
function Probe([string]$file) {
    $result = & $probe -v error -show_streams -show_format -of json -i $file
    if ($LASTEXITCODE -ne 0) { throw "ffprobe non legge il file $file" }
    return ($result | Out-String | ConvertFrom-Json)
}
# Win32 CommandLineToArgvW quoting for ProcessStartInfo on .NET Framework 4.x.
function Quote-Arg([string]$value) {
    return '"' + [regex]::Replace([regex]::Replace($value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}
function Start-Tool([string]$path, [string[]]$arguments, [string]$cwd, [bool]$read, [bool]$write) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $path
    $info.WorkingDirectory = $cwd
    $info.Arguments = (($arguments | ForEach-Object { Quote-Arg $_ }) -join ' ')
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $read
    $info.RedirectStandardInput = $write
    if ($read) { $info.StandardOutputEncoding = [Text.Encoding]::UTF8 }
    return [System.Diagnostics.Process]::Start($info)
}
$scratchDir = $null
$partial = $null
$renderer = $null
$encoder = $null
try {
    $settings = $null
    if (!$RuntimeConfig -and $env:LOCALAPPDATA) {
        $defaultConfig = Join-Path $env:LOCALAPPDATA 'InsuranceStudioM0-Runtime/runtime.json'
        if (Test-Path -LiteralPath $defaultConfig) { $RuntimeConfig = $defaultConfig }
    }
    if ($RuntimeConfig) { $settings = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $RuntimeConfig).ProviderPath) | ConvertFrom-Json }
    if ($interactive) {
        Add-Type -AssemblyName System.Windows.Forms
        $Clip = Select-Path '1. Clip video' 'Video|*.mp4;*.mov' $false
        $Voce = Select-Path '2. Traccia narrante' 'Audio|*.wav;*.mp3;*.m4a' $false
        $automatic = [System.Windows.Forms.MessageBox]::Show('Eseguire ASR e inferenza sul PC? Scegliere No per usare un SRT esistente.', 'Elaborazione locale', 'YesNoCancel')
        if ($automatic -eq 'Cancel') { throw 'Operazione annullata.' }
        if ($automatic -eq 'Yes' -and !$settings) {
            $Whisper = Select-Path 'whisper-cli.exe v1.7.6' 'Whisper|whisper-cli.exe' $false
            $ModelloAsr = Select-Path 'Modello Whisper small Q5_1' 'Modello|*.bin' $false
            $Llama = Select-Path 'llama-cli.exe b6000' 'Llama|llama-cli.exe' $false
            $ModelloLlm = Select-Path 'Modello Qwen2.5 0.5B Q4_K_M' 'Modello|*.gguf' $false
        } elseif ($automatic -eq 'No') { $Sottotitoli = Select-Path '3. Sottotitoli con tempi approvati' 'Sottotitoli|*.srt' $false }
        $Destinazione = Select-Path '4. Nuovo file master' 'Video MPEG-4|*.mp4' $true
    }
    if (!$Clip -or !$Voce -or !$Destinazione) { throw 'Indicare Clip, Voce e Destinazione.' }
    if ($settings) {
        $ffmpeg = $settings.Ffmpeg
        $probe = $settings.Ffprobe
        if (!$Sottotitoli) {
            if (!$Whisper) { $Whisper = $settings.Whisper }
            if (!$ModelloAsr) { $ModelloAsr = $settings.WhisperModel }
            if (!$Llama) { $Llama = $settings.Llama }
            if (!$ModelloLlm) { $ModelloLlm = $settings.QwenModel }
        }
    } else {
    $ffmpeg = (Get-Command ffmpeg.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    $probe = (Get-Command ffprobe.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    }
    $Clip = (Resolve-Path -LiteralPath $Clip).ProviderPath
    $Voce = (Resolve-Path -LiteralPath $Voce).ProviderPath
    if ($Sottotitoli) { $Sottotitoli = (Resolve-Path -LiteralPath $Sottotitoli).ProviderPath }
    elseif (!$Whisper -or !$ModelloAsr) { throw 'Servono un SRT oppure il motore e il modello Whisper.' }
    if ([bool]$Llama -ne [bool]$ModelloLlm) { throw 'Indicare insieme motore e modello LLM.' }
    $out = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Destinazione)
    if ([IO.Path]::GetExtension($out) -ine '.mp4' -or (Test-Path -LiteralPath $out)) { throw 'Scegliere un nuovo file con estensione .mp4.' }
    $video = @((Probe $Clip).streams | Where-Object codec_type -eq video)
    $audio = @((Probe $Voce).streams | Where-Object codec_type -eq audio)
    if ($video.Count -lt 1 -or $audio.Count -lt 1) { throw 'Manca la traccia video o narrante.' }
    $culture = [Globalization.CultureInfo]::InvariantCulture
    $seconds = [double]::Parse($audio[0].duration, $culture)
    if (![double]::IsNaN($seconds) -and $seconds -gt 0 -and $seconds -le 300) {
        $frames = [int][Math]::Ceiling($seconds * 30)
    } else { throw 'La durata della voce deve essere leggibile e compresa entro 300 secondi nello spike.' }
    $duration = ($frames / 30).ToString('0.000000000', $culture)
    $job = [Guid]::NewGuid().ToString('N')
    $scratchDir = Join-Path ([IO.Path]::GetTempPath()) "studio-$job"
    [void][IO.Directory]::CreateDirectory($scratchDir)
    $cuePath = Join-Path $scratchDir 'captions.srt'
    if ($Sottotitoli) {
        Copy-Item -LiteralPath $Sottotitoli -Destination $cuePath
    } else {
        $lock = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'asr.lock.json')) | ConvertFrom-Json
        Assert-Model $ModelloAsr $lock.model.sha256
        $wav = Join-Path $scratchDir 'asr.wav'
        & $ffmpeg -loglevel error -nostdin -i $Voce -map 0:a:0 -ac 1 -ar 16000 -c:a pcm_s16le $wav
        if ($LASTEXITCODE -ne 0) { throw 'Conversione PCM per ASR fallita.' }
        & $Whisper -m $ModelloAsr -f $wav -l it -t 2 -ng -ml 48 -sow -osrt -of (Join-Path $scratchDir 'captions')
        if ($LASTEXITCODE -ne 0) { throw 'Whisper non ha trascritto la voce.' }
    }
    $cues = @(Read-Cues $cuePath)
    if (!$cues.Count) { throw 'Nessun parlato temporizzato.' }
    $keyword = ''
    $inference = $null
    if ($Llama) {
        $inference = Invoke-LocalInference $cues[0].Text ((Resolve-Path -LiteralPath $Llama).ProviderPath) ((Resolve-Path -LiteralPath $ModelloLlm).ProviderPath) $scratchDir
        $keyword = $inference.keyword
    }
    if ([double]::Parse($video[0].duration,$culture)+1/30 -lt $seconds) {
        throw 'La clip deve coprire tutta la narrazione.'
    }
    foreach ($cue in $cues) {
        if ($cue.Start -lt 0 -or $cue.End -gt $seconds*1000+10) {
            throw 'Cue esterno alla durata della narrazione. Verificare i timestamp.'
        }
    }
    Write-Captions $cues $keyword (Join-Path $scratchDir 'captions.ass')
    [void][IO.Directory]::CreateDirectory((Join-Path $scratchDir 'fonts'))
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fonts/Inter.ttf') -Destination (Join-Path $scratchDir 'fonts/Inter.ttf')
    $sourceMeasure = Measure-Export $ffmpeg $Voce
    if (!$sourceMeasure.checks.livelli_misurabili) { throw 'Narrazione silenziosa o livelli non misurabili.' }
    $m = $sourceMeasure.loudnorm_measurements
    $normalizer = "loudnorm=I=-16:TP=-1.5:LRA=8:linear=true:measured_I=$($m.input_i):measured_TP=$($m.input_tp):measured_LRA=$($m.input_lra):measured_thresh=$($m.input_thresh):offset=$($m.target_offset),apad"
    $partial = Join-Path ([IO.Path]::GetDirectoryName($out)) ".$job.partial.mp4"
    $filters = "[0:v:0]fps=30,scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2:0x0A1628,setsar=1,tpad=stop_mode=clone:stop_duration=$duration[bg];[bg][2:v]overlay=(W-w)/2:(H-h)/2,ass=captions.ass:fontsdir=fonts[v]"
    $arguments = @('-hide_banner','-loglevel','warning','-n','-i',$Clip,'-i',$Voce,'-f','rawvideo','-pixel_format','rgba','-video_size','1080x1920','-framerate','30','-i','pipe:0','-filter_complex',$filters,'-map','[v]','-map','1:a:0','-af',$normalizer,'-t',$duration,'-c:v','libx264','-pix_fmt','yuv420p','-c:a','aac','-b:a','192k','-ar','48000','-movflags','+faststart',$partial)
    Write-Host "Render locale di $frames fotogrammi. Ctrl+C per interrompere."
    $encoder = Start-Tool $ffmpeg $arguments $scratchDir $false $true
    $renderer = Start-Tool (Join-Path $PSScriptRoot 'scene-frames.exe') @('1080','1920',"$frames") $scratchDir $true $false
    $transfer = $renderer.StandardOutput.BaseStream.CopyToAsync($encoder.StandardInput.BaseStream)
    while (!$transfer.IsCompleted) { Start-Sleep -Milliseconds 100 }
    [void]$transfer.GetAwaiter().GetResult()
    $encoder.StandardInput.Close()
    $renderer.WaitForExit()
    $encoder.WaitForExit()
    if ($renderer.ExitCode -ne 0 -or $encoder.ExitCode -ne 0) { throw 'Render fallito. Verificare disponibilita di libass/libx264, input e spazio libero.' }
    $media = Probe $partial
    $v = @($media.streams | Where-Object codec_type -eq video)[0]
    $a = @($media.streams | Where-Object codec_type -eq audio)[0]
    $ticks = @(& $probe -v error -select_streams v:0 -show_entries frame=best_effort_timestamp -of csv=p=0 -i $partial | ForEach-Object { if ($_ -match '^(-?\d+),?$') { [long]$Matches[1] } })
    if ($LASTEXITCODE -ne 0 -or $ticks.Count -ne $frames) { throw 'Conteggio dei timestamp video incoerente.' }
    $tb = $v.time_base -split '/'
    for ($n=0; $n -lt $frames; $n++) {
        if (($ticks[$n] * [long]$tb[0] * 30) -ne ($n * [long]$tb[1])) { throw "Cadenza diversa da CFR al frame $n." }
    }
    if ($v.width -ne 1080 -or $v.height -ne 1920 -or $v.codec_name -ne 'h264' -or $v.r_frame_rate -ne '30/1' -or [int]$v.nb_frames -ne $frames -or $a.codec_name -ne 'aac' -or $a.sample_rate -ne '48000' -or [Math]::Abs([double]::Parse($a.duration,$culture) - $frames/30) -gt 1/30) { throw 'Verifica dei flussi MP4 fallita.' }
    if ([Math]::Abs([double]::Parse($v.duration,$culture)-$frames/30) -gt 1/30) {
        throw 'Durata video fuori tolleranza.'
    }
    $quality = Measure-Export $ffmpeg $partial
    if ($quality.status -eq 'BLOCKED') {
        $failureReport = "$out.$job.qa-blocked.json"
        Write-Text $failureReport ($quality | ConvertTo-Json -Depth 6)
        throw "QA materiale fallita. Consultare $failureReport e correggere livelli audio o fotogrammi neri."
    }
    $report = "$out.$job"
    [void][IO.Directory]::CreateDirectory($report)
    try {
        [IO.File]::Copy($cuePath, (Join-Path $report 'sottotitoli.srt'))
        if ($inference) { Write-Text (Join-Path $report 'inferenza.json') ($inference | ConvertTo-Json -Depth 5) }
        Write-Text (Join-Path $report 'qa.json') ($quality | ConvertTo-Json -Depth 6)
        Write-Text (Join-Path $report 'ffprobe.json') ($media | ConvertTo-Json -Depth 10)
        [IO.File]::Move($partial, $out)
    } catch { [IO.Directory]::Delete($report, $true); throw }
    Write-Host "Esportato: $out"
    if ($interactive) { [void][System.Windows.Forms.MessageBox]::Show("File verificato: $out", 'Studio M0') }
} catch {
    if ($interactive) { [void][System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Studio M0') }
    Write-Error -ErrorAction Continue $_
    exit 1
} finally {
    foreach ($process in @($renderer, $encoder)) {
        if ($null -ne $process) {
            if (!$process.HasExited) { $process.Kill(); $process.WaitForExit() }
            $process.Dispose()
        }
    }
    if ($partial -and [IO.File]::Exists($partial)) { [IO.File]::Delete($partial) }
    if ($scratchDir -and [IO.Directory]::Exists($scratchDir)) { [IO.Directory]::Delete($scratchDir, $true) }
}
