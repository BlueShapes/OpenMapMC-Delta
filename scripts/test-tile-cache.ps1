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
$world = Join-Path $javaRoot 'world'
$client = Join-Path $javaRoot 'client'
$test = Join-Path $repository 'tests/TileCacheStorageTest.java'
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/tile-cache-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @(
    (Join-Path $world 'Tile.java'),
    (Join-Path $world 'TileStorage.java'),
    (Join-Path $world 'WorldCache.java'),
    (Join-Path $client 'CacheRevisionTracker.java'),
    $test
)
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Tile cache test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') "-Djava.io.tmpdir=$outputDirectory" -cp $outputDirectory net.aosankaku.openmapdelta.world.TileCacheStorageTest
if ($LASTEXITCODE -ne 0) { throw 'Tile cache tests failed.' }
