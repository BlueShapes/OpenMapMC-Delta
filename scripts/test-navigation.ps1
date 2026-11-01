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
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/navigation-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @('navigation/NavigationState.java', 'navigation/NavigationMapSelection.java',
    'navigation/NavigationRequestPolicy.java', 'marker/MarkerType.java') |
    ForEach-Object { Join-Path $sourceRoot $_ }
$tests = Join-Path $repository 'tests/NavigationStateTest.java'
$testWorldSelection = $VersionRoot -in @('1.20.1', '1.21.1', '26.2')
if ($testWorldSelection) {
    $sources += Join-Path $sourceRoot 'navigation/NavigationWorldSelection.java'
    $tests = @($tests, (Join-Path $repository 'tests/NavigationWorldSelectionTest.java'))
}
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $sources $tests
if ($LASTEXITCODE -ne 0) { throw 'Navigation test compilation failed.' }
$smartDeathpoints = $VersionRoot -in @('1.20.1', '1.21.1', '26.2')
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.navigation.NavigationStateTest $smartDeathpoints.ToString().ToLowerInvariant()
if ($LASTEXITCODE -ne 0) { throw 'Navigation tests failed.' }
if ($testWorldSelection) {
    & (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.navigation.NavigationWorldSelectionTest
    if ($LASTEXITCODE -ne 0) { throw 'Navigation world selection tests failed.' }
}
