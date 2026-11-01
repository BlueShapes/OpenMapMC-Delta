# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.2')][string]$VersionRoot = '1.21.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/network"
$output = Join-Path $repository "$VersionRoot/common/build/server-snapshot-connection-tests"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @('PlayerPosition.java', 'ClientServerState.java', 'ServerSnapshot.java') |
    ForEach-Object { Join-Path $javaRoot $_ }
$sources += Join-Path $repository 'tests/ServerSnapshotConnectionTest.java'
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Snapshot connection test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.network.ServerSnapshotConnectionTest
if ($LASTEXITCODE -ne 0) { throw 'Snapshot connection tests failed.' }
