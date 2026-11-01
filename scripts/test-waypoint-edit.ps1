# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/waypoint-edit-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @(
    (Join-Path $javaRoot 'gui/WaypointEditDraft.java'),
    (Join-Path $javaRoot 'navigation/NavigationArrivalSnapshot.java'),
    (Join-Path $repository 'tests/WaypointEditDraftTest.java')
)
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Waypoint edit helper test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory WaypointEditDraftTest
if ($LASTEXITCODE -ne 0) { throw 'Waypoint edit helper tests failed.' }
