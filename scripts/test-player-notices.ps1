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
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/player-notice-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$deathTests = if ($VersionRoot -in @('1.20.1', '1.21.1')) { @((Join-Path $repository 'tests/DeathPointNoticeTest.java')) } else { @() }
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    (Join-Path $javaRoot 'client/LocationNotice.java') `
    (Join-Path $javaRoot 'client/LocationNoticeOutbox.java') `
    (Join-Path $javaRoot 'client/LocationNoticeProtocol.java') `
    (Join-Path $javaRoot 'client/LocationNoticeDeliveryQueue.java') `
    (Join-Path $javaRoot 'client/PlayerCommandPayload.java') `
    (Join-Path $javaRoot 'client/LocationContextClient.java') `
    (Join-Path $javaRoot 'network/LocationContextRequest.java') `
    (Join-Path $javaRoot 'network/LocationContextResponse.java') `
    (Join-Path $repository 'tests/PlayerNoticesTest.java') `
    (Join-Path $repository 'tests/LocationContextClientTest.java') $deathTests
if ($LASTEXITCODE -ne 0) { throw 'Player notice test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.client.PlayerNoticesTest
if ($LASTEXITCODE -ne 0) { throw 'Player notice tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.client.LocationContextClientTest
if ($LASTEXITCODE -ne 0) { throw 'Location context client tests failed.' }
if ($VersionRoot -in @('1.20.1', '1.21.1')) {
    & (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.client.DeathPointNoticeTest
    if ($LASTEXITCODE -ne 0) { throw 'Deathpoint help-notice tests failed.' }
}
