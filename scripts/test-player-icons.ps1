# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$sourceRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/render"
$markerRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/player-icon-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$compileLog = Join-Path $outputDirectory 'compile.log'
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    (Join-Path $sourceRoot 'PlayerIconColor.java') `
    (Join-Path $sourceRoot 'FullscreenMapMarkerGeometry.java') `
    (Join-Path $markerRoot 'PlayerMarkerSizing.java') `
    (Join-Path $markerRoot 'MarkerIconSizing.java') `
    (Join-Path $repository 'tests/PlayerIconColorTest.java') `
    (Join-Path $repository 'tests/PlayerIconGeometryTest.java') *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 30
    throw 'Player icon test compilation failed.'
}
& (Join-Path $JdkHome 'bin/java.exe') '-Djava.awt.headless=true' -cp $outputDirectory net.aosankaku.openmapdelta.render.PlayerIconColorTest
if ($LASTEXITCODE -ne 0) { throw 'Player icon tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.PlayerIconGeometryTest
if ($LASTEXITCODE -ne 0) { throw 'Player icon geometry tests failed.' }
