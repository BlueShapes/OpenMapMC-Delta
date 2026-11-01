# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$repository = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker/MarkerHoverStyle.java"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/marker-hover-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $source `
    (Join-Path $repository 'tests/MarkerHoverStyleTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Marker hover test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.marker.MarkerHoverStyleTest
if ($LASTEXITCODE -ne 0) { throw 'Marker hover tests failed.' }
