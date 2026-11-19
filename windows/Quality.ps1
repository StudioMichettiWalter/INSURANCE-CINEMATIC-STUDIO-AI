# Misure automatiche del file codificato. Le valutazioni percettive restano umane.
function Measure-Export([string]$engine, [string]$path) {
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $engine
    $arguments = @('-nostdin','-hide_banner','-protocol_whitelist','file,pipe','-i',$path,'-vf','blackdetect=d=0.032:pix_th=0.04',
                   '-af','loudnorm=I=-16:TP=-1.5:LRA=8:print_format=json','-f','null','-')
    $start.Arguments = (($arguments | ForEach-Object { Quote-Arg $_ }) -join ' ')
    $start.UseShellExecute = $false
    $start.RedirectStandardError = $true
    $start.RedirectStandardOutput = $true
    $process = [Diagnostics.Process]::Start($start)
    try {
        $stderr = $process.StandardError.ReadToEndAsync()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $process.WaitForExit()
        $log = $stderr.GetAwaiter().GetResult()
        [void]$stdout.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw 'Analisi QA del file codificato fallita.' }
    } finally { $process.Dispose() }
    $match = [regex]::Match($log, '(?s)\{\s*"input_i"\s*:.*?\}')
    if (!$match.Success) { throw 'Misura loudness assente dal rapporto FFmpeg.' }
    $levels = $match.Value | ConvertFrom-Json
    $culture = [Globalization.CultureInfo]::InvariantCulture
    $loudness = [double]::NaN
    $peak = [double]::NaN
    $validLoudness = [double]::TryParse($levels.input_i, [Globalization.NumberStyles]::Float, $culture, [ref]$loudness)
    $validPeak = [double]::TryParse($levels.input_tp, [Globalization.NumberStyles]::Float, $culture, [ref]$peak)
    $black = @([regex]::Matches($log, 'black_start:[^\r\n]+') | ForEach-Object { $_.Value })
    $finite = $validLoudness -and $validPeak -and ![double]::IsNaN($loudness) -and ![double]::IsInfinity($loudness) -and
              ![double]::IsNaN($peak) -and ![double]::IsInfinity($peak)
    $checks = [ordered]@{
        livelli_misurabili = $finite
        loudness = $finite -and $loudness -ge -17 -and $loudness -le -13
        true_peak = $finite -and $peak -le -1
        nessun_frame_nero = $black.Count -eq 0
    }
    return [ordered]@{
        status = $(if (@($checks.Values | Where-Object { !$_ }).Count) { 'BLOCKED' } else { 'QA' })
        checks = $checks
        integrated_lufs = $levels.input_i
        true_peak_dbtp = $levels.input_tp
        loudnorm_measurements = $levels
        black_intervals = $black
        revisione_umana = 'NON ESEGUITO'
    }
}
