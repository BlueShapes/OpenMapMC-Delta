# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$MtrJar
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/compat"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/transit-rail-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    (Join-Path $javaRoot 'TransitStation.java') `
    (Join-Path $javaRoot 'TransitStationCenter.java') `
    (Join-Path $javaRoot 'TransitStationReader.java') `
    (Join-Path $javaRoot 'TransitStationCatalog.java') `
    (Join-Path $javaRoot 'TransitRail.java') `
    (Join-Path $javaRoot 'TransitRailCreateCache.java') `
    (Join-Path $javaRoot 'TransitRailReader.java') `
    (Join-Path $repository 'tests/TransitRailReaderTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Transit rail test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.compat.TransitRailReaderTest
if ($LASTEXITCODE -ne 0) { throw 'Transit rail tests failed.' }
if ($MtrJar) {
    $MtrJar = (Resolve-Path -LiteralPath $MtrJar).Path
    $mtrClasspath = "$outputDirectory;$MtrJar"
    & (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $mtrClasspath -d $outputDirectory `
        (Join-Path $repository 'tests/TransitMtrRailJarTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Installed MTR JAR rail test compilation failed.' }
    & (Join-Path $JdkHome 'bin/java.exe') -cp $mtrClasspath net.aosankaku.openmapdelta.compat.TransitMtrRailJarTest
    if ($LASTEXITCODE -ne 0) { throw 'Installed MTR JAR rail checks failed.' }
}
