# Copyright (c) 2026 AoSankaku. Licensed under Apache-2.0.
param(
    [ValidateSet('1.20.1','1.21.1','26.1.2','26.2','all')][string]$VersionRoot = 'all',
    [string]$JdkHome = 'C:/Program Files/Java/jdk-17'
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$cacheRoot = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1'
$gson = Get-ChildItem -LiteralPath $cacheRoot -Recurse -Filter 'gson-2.10.1.jar' | Select-Object -First 1 -ExpandProperty FullName
if (!$gson) { throw 'Cached Gson 2.10.1 is required.' }
$versions = if ($VersionRoot -eq 'all') { @('1.20.1','1.21.1','26.1.2','26.2') } else { @($VersionRoot) }
$nonce = [guid]::NewGuid().ToString('N')
$stubRoot = Join-Path $repository 'tests/external-player-client-stubs'
$fixtureRoot = Join-Path $repository 'tests/fixtures/installed_players'
$supportSources = @(Get-ChildItem -LiteralPath $stubRoot -Recurse -Filter '*.java' | ForEach-Object FullName)
$fixtureSources = @(Get-ChildItem -LiteralPath $fixtureRoot -Recurse -Filter '*.java' | ForEach-Object FullName)
foreach ($version in $versions) {
    $out = Join-Path $repository ".task-work/external-player-client-$nonce/$version"
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $root = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta"
    $sources = @(
        (Join-Path $root 'network/PlayerPosition.java'),
        (Join-Path $root 'network/ClientServerState.java'),
        (Join-Path $root 'compat/players/ExternalPlayerState.java'),
        (Join-Path $root 'compat/players/InstalledMapPlayers.java'),
        (Join-Path $root 'compat/players/BlueMapEndpoint.java'),
        (Join-Path $root 'compat/players/BlueMapConfig.java'),
        (Join-Path $root 'compat/players/ExternalPlayerClient.java'),
        (Join-Path $repository 'tests/ExternalPlayerClientTest.java')
    ) + $supportSources + $fixtureSources
    & (Join-Path $JdkHome 'bin/javac.exe') '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $gson -d $out $sources *> (Join-Path $out 'compile.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $out 'compile.log') -Tail 20; throw "External player client compile failed: $version" }
    & (Join-Path $JdkHome 'bin/java.exe') '-Duser.language=en' "-Djava.io.tmpdir=$out" "-Dexternal.player.test.dir=$out/config" -cp "$out;$gson" net.aosankaku.openmapdelta.compat.players.ExternalPlayerClientTest
    if ($LASTEXITCODE -ne 0) { throw "External player client tests failed: $version" }
}
