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
$output = Join-Path $repository "$VersionRoot/common/build/emojiful-icon-tests"
$production = Join-Path $output 'production'
$fixtures = Join-Path $output 'fixtures'
New-Item -ItemType Directory -Force -Path $production, $fixtures | Out-Null
$sources = @('compat/EmojifulIconRegistry.java', 'marker/MarkerIconId.java', 'marker/MarkerIconSearchQuery.java') |
    ForEach-Object { Join-Path $sourceRoot $_ }
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $production $sources (Join-Path $repository 'tests/EmojifulAbsentTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Emojiful bridge compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $production net.aosankaku.openmapdelta.compat.EmojifulAbsentTest
if ($LASTEXITCODE -ne 0) { throw 'Emojiful absent-classpath tests failed.' }
$fixtureSources = Get-ChildItem (Join-Path $repository 'tests/fixtures/emojiful') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $production -d $fixtures $fixtureSources (Join-Path $repository 'tests/EmojifulIconRegistryTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Emojiful fixture tests compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp "$production;$fixtures" net.aosankaku.openmapdelta.compat.EmojifulIconRegistryTest
if ($LASTEXITCODE -ne 0) { throw 'Emojiful registry tests failed.' }
