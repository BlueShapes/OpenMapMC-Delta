# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/sleep-point-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$source = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/client/SleepPointSession.java"
$sources = @((Join-Path $repository 'tests/SleepPointSessionTest.java'), (Join-Path $repository 'tests/SleepSpawnEvidenceTest.java'))
if (Test-Path -LiteralPath $source) { $sources += $source }
$evidence = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/client/SleepSpawnEvidence.java"
if (Test-Path -LiteralPath $evidence) { $sources += $evidence }
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Sleep point test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.client.SleepPointSessionTest
if ($LASTEXITCODE -ne 0) { throw 'Sleep point session tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.client.SleepSpawnEvidenceTest
if ($LASTEXITCODE -ne 0) { throw 'Sleep spawn evidence tests failed.' }
& (Join-Path $repository 'scripts/test-waypoint-metadata.ps1') -VersionRoot $VersionRoot -JdkHome $JdkHome
