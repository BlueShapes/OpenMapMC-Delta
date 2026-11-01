# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw 'JdkHome must contain bin/javac.exe and bin/java.exe.'
}

$sourceRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$platform = Join-Path $sourceRoot 'platform/DataDirectoryMigration.java'
$world = Join-Path $sourceRoot 'world'
$test = Join-Path $repository 'tests/DataDirectoryMigrationTest.java'
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/data-directory-migration-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @(
    $platform,
    (Join-Path $world 'Tile.java'),
    (Join-Path $world 'TileStorage.java'),
    $test
)
$fixtureRoot = Join-Path $outputDirectory 'fixtures'
& $javac '--release' '17' '-encoding' 'UTF-8' '-d' $outputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Data directory migration test compilation failed.' }
& $java '-Dfile.encoding=UTF-8' "-Dmap.migration.test.root=$fixtureRoot" `
    '-cp' $outputDirectory 'net.aosankaku.openmapdelta.platform.DataDirectoryMigrationTest'
if ($LASTEXITCODE -ne 0) { throw 'Data directory migration tests failed.' }
