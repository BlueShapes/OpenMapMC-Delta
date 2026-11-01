# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$sourceRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$output = Join-Path $repository "$VersionRoot/common/build/installed-map-player-tests"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @(
    (Join-Path $sourceRoot 'network/PlayerPosition.java'),
    (Join-Path $repository 'tests/ExternalPlayerStateTest.java'),
    (Join-Path $repository 'tests/InstalledMapPlayersTest.java')
)
$stateSource = Join-Path $sourceRoot 'compat/players/ExternalPlayerState.java'
$readerSource = Join-Path $sourceRoot 'compat/players/InstalledMapPlayers.java'
if (Test-Path -LiteralPath $stateSource) { $sources += $stateSource }
if (Test-Path -LiteralPath $readerSource) { $sources += $readerSource }
$fixtures = Get-ChildItem (Join-Path $repository 'tests/fixtures/installed_players') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName }
$sources += $fixtures
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Installed map player state tests failed to compile.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.compat.players.ExternalPlayerStateTest
if ($LASTEXITCODE -ne 0) { throw 'Installed map player state tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.compat.players.InstalledMapPlayersTest
if ($LASTEXITCODE -ne 0) { throw 'Installed map player reader tests failed.' }
