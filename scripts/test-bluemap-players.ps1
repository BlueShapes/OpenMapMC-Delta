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
foreach ($version in $versions) {
    $out = Join-Path $repository ".task-work/remote-players-20261020/bluemap-$version"
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $root = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta"
    $sources = @((Join-Path $root 'network/PlayerPosition.java'))
    foreach ($file in @('BlueMapEndpoint','BlueMapPlayers','BlueMapHttp','BlueMapConfig')) {
        $source = Join-Path $root "compat/players/$file.java"
        if (Test-Path -LiteralPath $source) { $sources += $source }
    }
    $sources += Join-Path $repository 'tests/BlueMapPlayersTest.java'
    & (Join-Path $JdkHome 'bin/javac.exe') '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $gson -d $out $sources *> (Join-Path $out 'compile.log')
    if ($LASTEXITCODE -ne 0) { Get-Content (Join-Path $out 'compile.log') -Tail 12; throw "BlueMap compile failed: $version" }
    & (Join-Path $JdkHome 'bin/java.exe') '-Duser.language=en' "-Djava.io.tmpdir=$out" -cp "$out;$gson" net.aosankaku.openmapdelta.compat.players.BlueMapPlayersTest
    if ($LASTEXITCODE -ne 0) { throw "BlueMap tests failed: $version" }
}
