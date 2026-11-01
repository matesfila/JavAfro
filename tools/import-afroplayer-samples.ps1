param(
    [Parameter(Mandatory = $true)][string]$SourceRoot,
    [string]$Ffmpeg = 'ffmpeg'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sampleSourceRoot = [System.IO.Path]::GetFullPath($SourceRoot)
Push-Location $projectRoot
try {
    # Uses the YAML package installed by npm ci in web/.
    $readManifest = @'
const fs = require('node:fs');
const { parse } = require('./web/node_modules/yaml');
const manifest = parse(fs.readFileSync('src/main/resources/samples/afroplayer-samples.yaml', 'utf8'));
// The 29 Tomas Marek djembe samples were imported separately; this converts the other 54.
process.stdout.write(JSON.stringify([...new Set(Object.values(manifest.banks).flatMap(bank => bank.samples))].filter(file => !file.startsWith('djembe/'))));
'@
    $sampleJson = $readManifest | & node
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the sample manifest. Run npm ci in web/ first.' }
    $files = $sampleJson | ConvertFrom-Json
    foreach ($file in $files) {
        $sourceFolder = if ($file.StartsWith('djembe2/')) { 'djembe_kontakt' } else { 'dunduns_rudo' }
        $sourceFile = Join-Path (Join-Path $sampleSourceRoot $sourceFolder) ([IO.Path]::GetFileNameWithoutExtension($file) + '.flac')
        $outputFile = [IO.Path]::GetFullPath((Join-Path $projectRoot ('src/main/resources/samples/' + $file)))
        $outputRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot 'src/main/resources/samples'))
        if (-not $outputFile.StartsWith($outputRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Sample path is outside the project resource directory: $file"
        }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputFile) | Out-Null
        & $Ffmpeg -nostdin -hide_banner -loglevel error -y -i $sourceFile -map_metadata -1 -ac 1 -ar 44100 -c:a pcm_s16le $outputFile
        if ($LASTEXITCODE -ne 0) { throw "Conversion failed: $sourceFile" }
    }
    Write-Output "Converted $($files.Count) AfroPlayer samples without normalization, trimming or pitch changes."
} finally { Pop-Location }
