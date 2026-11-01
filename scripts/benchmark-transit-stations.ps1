# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/compat"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/transit-station-benchmark"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    (Join-Path $javaRoot 'TransitStation.java') `
    (Join-Path $javaRoot 'TransitStationCenter.java') `
    (Join-Path $javaRoot 'TransitStationReader.java') `
    (Join-Path $javaRoot 'TransitStationCatalog.java') `
    (Join-Path $repository 'tests/TransitStationBenchmark.java')
if ($LASTEXITCODE -ne 0) { throw 'Transit benchmark compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.compat.TransitStationBenchmark
if ($LASTEXITCODE -ne 0) { throw 'Transit benchmark failed.' }
