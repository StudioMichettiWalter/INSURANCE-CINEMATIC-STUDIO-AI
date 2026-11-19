function Write-Text([string]$path, [string]$text) {
    [IO.File]::WriteAllText($path, $text, [Text.UTF8Encoding]::new($false))
}
function Assert-Model([string]$path, [string]$expected) {
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ine $expected) { throw "SHA-256 del modello non valido: $path" }
}
function Invoke-LocalInference([string]$transcript, [string]$enginePath, [string]$modelPath, [string]$dir) {
    $lock = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'dependencies.lock.json')) | ConvertFrom-Json
    Assert-Model $modelPath $lock.model.sha256
    $tokens = @([regex]::Matches($transcript, "[\p{L}\p{N}]+(?:['\u2019][\p{L}\p{N}]+)*"))
    if ($tokens.Count -eq 0 -or $tokens.Count -gt 240 -or $transcript.Length -gt 2500) { throw 'Il segmento per il modello supera i limiti dello spike.' }
    $words = @($tokens | ForEach-Object { $_.Value } | Select-Object -Unique)
    $spans = @(
        for ($i=0; $i -lt $tokens.Count; $i++) {
            for ($j=$i+1; $j -lt [Math]::Min($i+5,$tokens.Count); $j++) {
                $phrase = $transcript.Substring($tokens[$i].Index, $tokens[$j].Index+$tokens[$j].Length-$tokens[$i].Index)
                if ($phrase.Length -le 55 -and $phrase -notmatch '[.!?\r\n]') { $phrase }
            }
        }
    )
    $titles = @($spans | Select-Object -Unique)
    if ($titles.Count -eq 0) { $titles = $words }
    $schema = @{type='object'; properties=@{keyword=@{type='string';enum=$words};title=@{type='string';enum=$titles}}; required=@('keyword','title');additionalProperties=$false}
    $system = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'prompt.it.txt')).Trim()
    $data = (@{trascrizione=$transcript} | ConvertTo-Json -Compress).Replace('<','\u003c').Replace('>','\u003e')
    $promptPath = Join-Path $dir 'prompt.txt'
    $schemaPath = Join-Path $dir 'schema.json'
    Write-Text $promptPath "<|im_start|>system`n$system<|im_end|>`n<|im_start|>user`n$data<|im_end|>`n<|im_start|>assistant`n"
    Write-Text $schemaPath ($schema | ConvertTo-Json -Depth 8 -Compress)
    $proc = Start-Tool $enginePath @('-m',$modelPath,'-f',$promptPath,'-jf',$schemaPath,'--no-conversation','--no-display-prompt','--no-escape','--no-warmup','--temp','0','--seed','42','-n','120','-c','2048','-t','2','-ngl','0','--simple-io') $dir $true $true
    try {
        $proc.StandardInput.Close()
        $response = $proc.StandardOutput.ReadToEndAsync()
        if (!$proc.WaitForExit(120000)) { throw 'Timeout inferenza locale (120 s).' }
        if ($proc.ExitCode -ne 0) { throw 'llama-cli non ha completato la generazione.' }
        $answer = (($response.GetAwaiter().GetResult().Trim() -replace '\[end of text\]\s*$','') | ConvertFrom-Json)
        if (@($answer.PSObject.Properties).Count -ne 2 -or $words -cnotcontains $answer.keyword -or $titles -cnotcontains $answer.title) { throw 'Inferenza JSON fuori schema.' }
        return [ordered]@{role='Video Editor';status='PROPOSED';keyword=$answer.keyword;title=$answer.title;model='Qwen2.5-0.5B-Instruct-Q4_K_M';engine='llama.cpp b6000';model_sha256=$lock.model.sha256;sources=@('prima frase della trascrizione locale');decision='Selezione estrattiva di titolo e parola da evidenziare, da approvare.'}
    } finally { if (!$proc.HasExited) { $proc.Kill(); $proc.WaitForExit() }; $proc.Dispose() }
}
function Read-Cues([string]$file) {
    $inputText = [IO.File]::ReadAllText($file).Replace("`r","").Trim()
    $lastEnd = 0
    foreach ($block in ($inputText -split '\n\s*\n')) {
        $match = [regex]::Match($block, '(?s)^\s*\d+\n(\d\d):(\d\d):(\d\d),(\d{3}) --> (\d\d):(\d\d):(\d\d),(\d{3})\s*\n(.+)$')
        if (!$match.Success) { throw 'Cue SRT non valido.' }
        $g = $match.Groups
        $ms1 = (([int]$g[1].Value*60+[int]$g[2].Value)*60+[int]$g[3].Value)*1000+[int]$g[4].Value
        $ms2 = (([int]$g[5].Value*60+[int]$g[6].Value)*60+[int]$g[7].Value)*1000+[int]$g[8].Value
        if ([int]$g[2].Value -ge 60 -or [int]$g[3].Value -ge 60 -or [int]$g[6].Value -ge 60 -or [int]$g[7].Value -ge 60) { throw 'Timestamp SRT fuori intervallo.' }
        if ($ms2 -le $ms1 -or $ms1 -lt $lastEnd) { throw 'Cue sovrapposto o senza durata.' }
        $lastEnd = $ms2
        [pscustomobject]@{ Start=$ms1; End=$ms2; Text=($g[9].Value -replace '\s+',' ').Trim() }
    }
}
function AssTime([int]$ms) {
    $cs = [int][Math]::Floor($ms/10)
    return '{0}:{1:00}:{2:00}.{3:00}' -f [int][Math]::Floor($cs/360000), ([int][Math]::Floor($cs/6000)%60), ([int][Math]::Floor($cs/100)%60), ($cs%100)
}
function Write-Captions([object[]]$cues, [string]$keyword, [string]$path) {
    $rows = [Collections.Generic.List[string]]::new()
    $rows.Add(@'
[Script Info]
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920
WrapStyle: 2
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Studio,Inter,48,&H00FFFFFF,&H0077C7E6,&H0028160A,&H0028160A,-1,0,0,0,100,100,0,0,1,3,0,2,96,96,260,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
'@)
    foreach ($cue in $cues) {
        if ($cue.Text -match '[{}\\]') { throw 'Correggere i caratteri di controllo ASS nella didascalia.' }
        $lines = [Collections.Generic.List[string]]::new()
        $line = ''
        foreach ($word in ($cue.Text -split ' ')) {
            if ($word.Length -gt 32) { throw 'Parola troppo lunga nel sottotitolo.' }
            if ($line -and $line.Length+1+$word.Length -gt 32) { $lines.Add($line); $line='' }
            $line = ($line+' '+$word).TrimStart()
        }
        if ($line) { $lines.Add($line) }
        if ($lines.Count -gt 2) { throw 'Suddividere il cue SRT per restare entro due righe.' }
        $label = $lines -join '\N'
        if ($keyword) {
            $pattern = '(?i)(?<![\p{L}\p{N}])' + [regex]::Escape($keyword) + '(?![\p{L}\p{N}])'
            $label = [regex]::Replace($label,$pattern,'{\c&H77C7E6&}$0{\c&HFFFFFF&}')
        }
        $rows.Add("Dialogue: 0,$(AssTime $cue.Start),$(AssTime $cue.End),Studio,,0,0,0,,$label")
    }
    Write-Text $path ($rows -join "`n")
}
