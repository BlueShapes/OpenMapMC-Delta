# Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2', 'all')]
    [string]$VersionRoot = 'all',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { $JdkHome = 'C:\Program Files\Java\jdk-17' }
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw "JDK not found under '$JdkHome'. Pass -JdkHome with JDK 17."
}

function Quote-ProcessArgument([string]$value) {
    if ($value -match '\s') { return '"' + $value.Replace('"', '\"') + '"' }
    return $value
}

$versions = if ($VersionRoot -eq 'all') { @('1.20.1', '1.21.1', '26.1.2', '26.2') } else { @($VersionRoot) }
$stubRoot = Join-Path $repository 'tests/minimap-texture-stubs'
$stubSources = @(Get-ChildItem -LiteralPath $stubRoot -Filter '*.java' -Recurse |
    ForEach-Object FullName)
$outputRoot = Join-Path $repository '.task-work/fullscreen-texture-tests'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

foreach ($version in $versions) {
    $common = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta"
    $sourceFiles = @(
        (Join-Path $common 'world/Tile.java'),
        (Join-Path $common 'world/TerrainRelief.java'),
        (Join-Path $common 'world/TileColorAverage.java'),
        (Join-Path $common 'render/FullscreenMapZoom.java'),
        (Join-Path $common 'render/FullscreenMapViewport.java'),
        (Join-Path $common 'render/FullscreenTerrainRaster.java'),
        (Join-Path $common 'render/FullscreenMapTexture.java'),
        (Join-Path $repository 'tests/FullscreenMapTextureTest.java')
    )
    $missing = @($sourceFiles | Where-Object { !(Test-Path -LiteralPath $_) })
    if ($missing.Count) { throw "$version is missing required source: $($missing -join ', ')" }

    $out = Join-Path $outputRoot $version
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $compileOut = Join-Path $outputRoot "$version-compile.out.log"
    $compileErr = Join-Path $outputRoot "$version-compile.err.log"
    $compileArgs = @('--release', '17', '-encoding', 'UTF-8', '-d', $out) + $stubSources + $sourceFiles
    $compileArgs = @($compileArgs | ForEach-Object { Quote-ProcessArgument ([string]$_) })
    $compile = Start-Process -FilePath $javac -ArgumentList $compileArgs -WorkingDirectory $repository `
        -RedirectStandardOutput $compileOut -RedirectStandardError $compileErr -Wait -PassThru -NoNewWindow
    if ($compile.ExitCode -ne 0) {
        if (Test-Path -LiteralPath $compileOut) { Get-Content -LiteralPath $compileOut -Tail 8 }
        if (Test-Path -LiteralPath $compileErr) { Get-Content -LiteralPath $compileErr -Tail 30 }
        throw "$version focused harness compilation failed (exit $($compile.ExitCode))."
    }
    Write-Output "$($version): compiled real FullscreenMapTexture and raster with --release 17"

    $stdout = Join-Path $outputRoot "$version-test.out.log"
    $stderr = Join-Path $outputRoot "$version-test.err.log"
    $runArgs = @('-cp', $out, 'net.aosankaku.openmapdelta.render.FullscreenMapTextureTest') |
        ForEach-Object { Quote-ProcessArgument ([string]$_) }
    $run = Start-Process -FilePath $java -ArgumentList $runArgs -WorkingDirectory $repository `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr -Wait -PassThru -NoNewWindow
    if ($run.ExitCode -ne 0) {
        Write-Output "$($version): FAIL (exit $($run.ExitCode))"
        if (Test-Path -LiteralPath $stdout) { Get-Content -LiteralPath $stdout -Tail 3 }
        if (Test-Path -LiteralPath $stderr) { Get-Content -LiteralPath $stderr -Tail 15 }
        throw "$version focused texture runtime check failed."
    }
    Get-Content -LiteralPath $stdout -Tail 1
    Write-Output "$($version): PASS"
}

Write-Output "Verified fullscreen texture copy and retry behavior for $($versions.Count) version root(s)."
