param(
    [string]$JarPath
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Choose the previously downloaded preview1 JAR if no argument was supplied.
if ([string]::IsNullOrWhiteSpace($JarPath)) {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Title = 'Choose your existing Moves & More 1.13 experimental JAR'
    $dialog.Filter = 'Fabric JAR files (*.jar)|*.jar'
    if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
        Write-Host 'Cancelled. Nothing was changed.'
        return
    }
    $JarPath = $dialog.FileName
}
$full = [System.IO.Path]::GetFullPath($JarPath)
if (-not [System.IO.File]::Exists($full)) { throw "JAR not found: $full" }
if (-not $full.EndsWith('.jar', [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Select a .jar file.' }

$folder = [System.IO.Path]::GetDirectoryName($full)
$stem = [System.IO.Path]::GetFileNameWithoutExtension($full)
$outPath = [System.IO.Path]::Combine($folder, $stem + '_ENCHANT_HOTFIX.jar')
$tmpPath = $outPath + '.partial'
if ([System.IO.File]::Exists($outPath) -or [System.IO.File]::Exists($tmpPath)) {
    throw "Output exists already: $outPath (rename or move it first)"
}

# The preview1 renderer uses this invalid identifier fallback.
# Both strings have exactly nine ASCII bytes, preserving the .class file structure.
$target = 'dev/kem108/actionsandstuff/client/render/actor/BedrockMaterials$MaterialProps.class'
$oldMark = '<unknown>'
$newMark = '_unknown_'
$source = $null
$dest = $null
$success = $false
$patched = $false
try {
    $source = [System.IO.Compression.ZipFile]::OpenRead($full)
    $modEntry = $source.GetEntry('fabric.mod.json')
    if ($null -eq $modEntry) { throw 'Not a Fabric JAR: missing fabric.mod.json.' }
    $reader = [System.IO.StreamReader]::new($modEntry.Open(), [System.Text.Encoding]::UTF8)
    try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if (-not $metadata.Contains('1.12.0+ans1.13.preview1')) {
        throw 'This patch ONLY supports Moves & More 1.13 preview1. It will not modify other builds.'
    }
    if ($null -eq $source.GetEntry($target)) { throw 'Target class missing: incompatible JAR.' }

    Write-Host 'Rebuilding your JAR with the Food tab enchantment hotfix...'
    $dest = [System.IO.Compression.ZipFile]::Open($tmpPath, [System.IO.Compression.ZipArchiveMode]::Create)
    foreach ($entry in $source.Entries) {
        $newEntry = $dest.CreateEntry($entry.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
        $newEntry.LastWriteTime = $entry.LastWriteTime
        if ($entry.FullName.EndsWith('/')) { continue }
        $inputStream = $entry.Open()
        $outputStream = $newEntry.Open()
        try {
            if ($entry.FullName -eq $target) {
                $mem = [System.IO.MemoryStream]::new()
                try {
                    $inputStream.CopyTo($mem)
                    $bytes = $mem.ToArray()
                } finally { $mem.Dispose() }
                $asText = [System.Text.Encoding]::ASCII.GetString($bytes)
                $pos = $asText.IndexOf($oldMark, [System.StringComparison]::Ordinal)
                if ($pos -lt 0 -or $asText.IndexOf($oldMark, $pos + 1, [System.StringComparison]::Ordinal) -ge 0) {
                    throw 'Expected exactly one <unknown> constant. No changes made.'
                }
                $replacement = [System.Text.Encoding]::ASCII.GetBytes($newMark)
                [System.Array]::Copy($replacement, 0, $bytes, $pos, $replacement.Length)
                $outputStream.Write($bytes, 0, $bytes.Length)
                $patched = $true
            }
            elseif ($entry.FullName -eq 'fabric.mod.json') {
                $updated = $metadata.Replace('1.12.0+ans1.13.preview1', '1.12.0+ans1.13.preview2')
                $bytes = [System.Text.Encoding]::UTF8.GetBytes($updated)
                $outputStream.Write($bytes, 0, $bytes.Length)
            }
            else { $inputStream.CopyTo($outputStream) }
        }
        finally {
            $outputStream.Dispose()
            $inputStream.Dispose()
        }
    }
    if (-not $patched) { throw 'Patch was not applied.' }
    $dest.Dispose(); $dest = $null
    $source.Dispose(); $source = $null
    [System.IO.File]::Move($tmpPath, $outPath)
    $success = $true
    Write-Host ''
    Write-Host 'SUCCESS: Patched JAR created' -ForegroundColor Green
    Write-Host $outPath
    Write-Host 'Remove the old JAR from mods. Install ONLY the new _ENCHANT_HOTFIX.jar.'
    Write-Host 'Test Creative > Food & Drinks in a disposable world.'
} finally {
    if ($null -ne $dest) { $dest.Dispose() }
    if ($null -ne $source) { $source.Dispose() }
    if (-not $success -and [System.IO.File]::Exists($tmpPath)) {
        [System.IO.File]::Delete($tmpPath)
    }
}
