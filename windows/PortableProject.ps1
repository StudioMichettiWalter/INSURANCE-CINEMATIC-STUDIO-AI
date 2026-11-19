# ZIP di lavorazione condiviso con Android, con nomi fissi e SHA-256.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$portableNames = @('clip.mp4','voce.audio','captions.srt')
$portableLimits = @(768MB,128MB,1MB)
$portableMagic = 'INSURANCE-STUDIO-RENDER/1'

function Copy-PortableStream([IO.Stream]$source, [IO.Stream]$target, [long]$limit) {
    $buffer = New-Object byte[] 65536
    [long]$total = 0
    while (($count = $source.Read($buffer, 0, $buffer.Length)) -gt 0) {
        $total += $count
        if ($total -gt $limit) { throw 'Un file supera il limite del formato portabile.' }
        $target.Write($buffer,0,$count)
    }
    if ($total -eq 0) { throw 'File vuoto nell''archivio portabile.' }
    return $total
}

function Get-PortableHashes([string]$clip, [string]$voice, [string]$captions) {
    $hashes = [ordered]@{}
    $files = @($clip,$voice,$captions)
    for ($i=0; $i -lt $files.Count; $i++) {
        $hashes[$portableNames[$i]] = (Get-FileHash -LiteralPath $files[$i] -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    return $hashes
}

function Read-PortableProject([string]$path, [string]$emptyDirectory) {
    if (@(Get-ChildItem -LiteralPath $emptyDirectory -Force).Count -ne 0) { throw 'Serve una cartella di lavoro vuota.' }
    $source = [IO.File]::Open($path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    try {
        if ($source.Length -le 0 -or $source.Length -gt 1GB) { throw 'L''archivio ZIP deve essere compreso tra 1 byte e 1 GiB.' }
        $archiveHash = (Get-FileHash -InputStream $source -Algorithm SHA256).Hash.ToLowerInvariant()
        $source.Position = 0
        $zip = [IO.Compression.ZipArchive]::new($source,[IO.Compression.ZipArchiveMode]::Read,$true)
        try {
            $allowed = $portableNames + 'studio-render.txt'
            $seen = New-Object 'System.Collections.Generic.HashSet[string]'
            foreach ($entry in $zip.Entries) {
                if ($allowed -cnotcontains $entry.FullName -or !$seen.Add($entry.FullName)) {
                    throw 'Voce ZIP sconosciuta o duplicata.'
                }
            }
            if ($seen.Count -ne $allowed.Count) { throw 'Archivio portabile incompleto.' }
            $manifest = $zip.GetEntry('studio-render.txt')
            $memory = [IO.MemoryStream]::new()
            $stream = $manifest.Open()
            try {
                if ($manifest.Length -ne (Copy-PortableStream $stream $memory 512)) { throw 'Manifest troncato.' }
                $lines = [Text.Encoding]::ASCII.GetString($memory.ToArray()) -split '\r?\n'
            } finally { $stream.Dispose(); $memory.Dispose() }
            if ($lines.Count -ne 5 -or $lines[0] -cne $portableMagic -or $lines[4] -cne '') {
                throw 'Versione o formato del manifest portabile non supportato.'
            }
            for ($i=0; $i -lt $portableNames.Count; $i++) {
                $name = $portableNames[$i]
                $prefix = "$name="
                if ($lines[$i+1] -cnotmatch ('^'+[regex]::Escape($prefix)+'[0-9a-f]{64}$')) { throw 'Hash non valido nel manifest portabile.' }
                $entry = $zip.GetEntry($name)
                if ($entry.Length -le 0 -or $entry.Length -gt $portableLimits[$i]) { throw "Dimensione ZIP incompatibile: $name" }
                $file = Join-Path $emptyDirectory $name
                $src = $entry.Open()
                try {
                    $dst = [IO.File]::Open($file,[IO.FileMode]::CreateNew)
                    try { $length = Copy-PortableStream $src $dst $portableLimits[$i] } finally { $dst.Dispose() }
                } finally { $src.Dispose() }
                $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
                if ($length -ne $entry.Length -or $lines[$i+1] -cne ($prefix+$hash)) { throw "File alterato nell'archivio: $name" }
            }
            return [pscustomobject]@{
                archive_sha256=$archiveHash
                Clip=(Join-Path $emptyDirectory 'clip.mp4')
                Voce=(Join-Path $emptyDirectory 'voce.audio')
                Sottotitoli=(Join-Path $emptyDirectory 'captions.srt')
            }
        } finally { $zip.Dispose() }
    } finally { $source.Dispose() }
}

function Write-PortableProject([string]$clip, [string]$voice, [string]$captions, [string]$destination) {
    $files = @($clip,$voice,$captions)
    $temporary = Join-Path ([IO.Path]::GetDirectoryName($destination)) ([guid]::NewGuid().ToString('N')+'.part')
    try {
        $out = [IO.File]::Open($temporary,[IO.FileMode]::CreateNew)
        try {
            $zip = [IO.Compression.ZipArchive]::new($out,[IO.Compression.ZipArchiveMode]::Create,$true)
            try {
                $manifest = [Text.StringBuilder]::new().Append($portableMagic).Append("`n")
                for ($i=0; $i -lt $files.Count; $i++) {
                    $name = $portableNames[$i]
                    $src = [IO.File]::Open($files[$i],[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
                    try {
                        $dst = $zip.CreateEntry($name).Open()
                        try { [void](Copy-PortableStream $src $dst $portableLimits[$i]) } finally { $dst.Dispose() }
                        $src.Position = 0
                        $hash = (Get-FileHash -InputStream $src -Algorithm SHA256).Hash.ToLowerInvariant()
                        [void]$manifest.Append($name).Append('=').Append($hash).Append("`n")
                    } finally { $src.Dispose() }
                }
                $stream = $zip.CreateEntry('studio-render.txt').Open()
                try {
                    $bytes = [Text.Encoding]::ASCII.GetBytes($manifest.ToString())
                    $stream.Write($bytes,0,$bytes.Length)
                } finally { $stream.Dispose() }
            } finally { $zip.Dispose() }
        } finally { $out.Dispose() }
        [IO.File]::Move($temporary,$destination)
    } finally { if ([IO.File]::Exists($temporary)) { [IO.File]::Delete($temporary) } }
}
