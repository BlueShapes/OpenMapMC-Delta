# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/context-action-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    (Join-Path $javaRoot 'marker/TransientMarkerLifetime.java') `
    (Join-Path $javaRoot 'client/LocationNotice.java') `
    (Join-Path $javaRoot 'client/LocationNoticeOutbox.java') `
    (Join-Path $repository 'tests/ContextActionsTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Context action test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.client.ContextActionsTest
if ($LASTEXITCODE -ne 0) { throw 'Context action tests failed.' }
