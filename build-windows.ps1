$ErrorActionPreference = 'Stop'

if ($env:OS -ne 'Windows_NT') {
    throw 'Windows packaging must run on Windows.'
}
if (-not $env:JAVA_HOME) {
    throw 'Set JAVA_HOME to a JDK 25 installation containing jpackage.'
}
$jpackage = Join-Path $env:JAVA_HOME 'bin/jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackage)) {
    throw "jpackage was not found at $jpackage"
}

Push-Location $PSScriptRoot
try {
    & mvn package
    if ($LASTEXITCODE -ne 0) {
        throw 'Maven build failed.'
    }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') '-Djava.awt.headless=true' 'tools/ObfuscationSmokeCheck.java'
    if ($LASTEXITCODE -ne 0) {
        throw 'Obfuscated JAR verification failed.'
    }

    # Include modules required by bundled libraries as well as application code.
    $jdeps = Join-Path $env:JAVA_HOME 'bin/jdeps.exe'
    $runtimeModules = & $jdeps --multi-release 25 --print-module-deps 'target/JavAfro-obfuscated.jar'
    if ($LASTEXITCODE -ne 0) {
        throw 'Runtime module analysis failed.'
    }
    $runtimeModules = ($runtimeModules -join '').Trim()
    if ($runtimeModules -notmatch '^java\.[a-z.]+(,java\.[a-z.]+)*$') {
        throw "Unexpected runtime module list: $runtimeModules"
    }

    $targetDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'target'))
    $inputDirectory = Join-Path $targetDirectory 'windows-input'
    $outputDirectory = Join-Path $targetDirectory 'windows'
    $appDirectory = Join-Path $outputDirectory 'JavAfro'

    # Replace only generated packaging folders, verified to be inside target.
    foreach ($generatedDirectory in @($inputDirectory, $appDirectory)) {
        $absolutePath = [System.IO.Path]::GetFullPath($generatedDirectory)
        if (-not $absolutePath.StartsWith($targetDirectory + [System.IO.Path]::DirectorySeparatorChar,
                [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove a directory outside target: $absolutePath"
        }
        if (Test-Path -LiteralPath $absolutePath) {
            Remove-Item -LiteralPath $absolutePath -Recurse -Force
        }
    }

    New-Item -ItemType Directory -Path $inputDirectory -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $targetDirectory 'JavAfro-obfuscated.jar') -Destination (Join-Path $inputDirectory 'JavAfro.jar')
    [xml]$projectPom = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'pom.xml') -Raw
    $packageArguments = @(
        '--type', 'app-image',
        '--name', 'JavAfro',
        '--app-version', $projectPom.project.version,
        '--vendor', 'Picosoft',
        '--description', 'African rhythm player',
        '--input', $inputDirectory,
        '--dest', $outputDirectory,
        '--main-jar', 'JavAfro.jar',
        '--main-class', 'picosoft.javafro.AfroJavaPlayer',
        '--add-modules', $runtimeModules,
        # Retain java.exe so the generated runtime can be verified and diagnosed.
        '--jlink-options', '--strip-debug --no-man-pages --no-header-files'
    )
    & $jpackage @packageArguments
    if ($LASTEXITCODE -ne 0) {
        throw 'Windows packaging failed.'
    }
    $smokeClasses = Join-Path $targetDirectory 'obfuscation-smoke'
    & (Join-Path $env:JAVA_HOME 'bin/javac.exe') -d $smokeClasses 'tools/ObfuscationSmokeCheck.java'
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not compile packaged runtime verification.'
    }
    & (Join-Path $appDirectory 'runtime/bin/java.exe') '-Djava.awt.headless=true' -cp $smokeClasses ObfuscationSmokeCheck (Join-Path $appDirectory 'app/JavAfro.jar')
    if ($LASTEXITCODE -ne 0) {
        throw 'Packaged Java runtime verification failed.'
    }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'javafro.yaml') -Destination $appDirectory
    Write-Host "Application created: $appDirectory\JavAfro.exe"
    Write-Host 'Distribute the entire JavAfro folder, including app and runtime.'
} finally {
    Pop-Location
}
