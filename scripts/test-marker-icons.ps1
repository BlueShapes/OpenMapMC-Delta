# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$markerRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/marker-icon-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @('MarkerIconId.java', 'MarkerIconSizing.java', 'PlayerMarkerSizing.java', 'MarkerIconSearchQuery.java') | ForEach-Object { Join-Path $markerRoot $_ }
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $sources (Join-Path $repository 'tests/MarkerIconTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Marker icon test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.marker.MarkerIconTest
if ($LASTEXITCODE -ne 0) { throw 'Marker icon tests failed.' }
