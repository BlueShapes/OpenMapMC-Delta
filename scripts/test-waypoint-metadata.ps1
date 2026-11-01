# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$gsonRoot = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
$gsonJar = Get-ChildItem -Path $gsonRoot -Filter 'gson-*.jar' -Recurse |
    Sort-Object FullName -Descending | Select-Object -First 1
if (!$gsonJar) { throw "Gson was not found under $gsonRoot." }
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker"
$platformRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/platform"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/waypoint-metadata-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$fixtureRoot = Join-Path $outputDirectory 'fixtures'
New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
$stubSources = Get-ChildItem -Path (Join-Path $repository 'tests/waypoint-metadata-stubs') -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName }
$sources = @(
    (Join-Path $javaRoot 'Marker.java'),
    (Join-Path $javaRoot 'MarkerIconId.java'),
    (Join-Path $javaRoot 'MarkerType.java'),
    (Join-Path $javaRoot 'MarkerStorage.java'),
    (Join-Path $javaRoot 'WaypointFileStore.java'),
    (Join-Path $javaRoot 'WaypointListPolicy.java'),
    (Join-Path $javaRoot 'TransitWaypointAppearanceStore.java'),
    (Join-Path $platformRoot 'DataDirectoryMigration.java'),
    (Join-Path $platformRoot 'DataPaths.java'),
    (Join-Path $repository 'tests/WaypointMetadataTest.java')
) + $stubSources
if ($VersionRoot -in @('1.20.1', '1.21.1', '26.2')) {
    $deathRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/death"
    $sources += @('DeathPointData.java', 'DeathPointMotion.java', 'DeathPointUpdate.java', 'DeathPointTombstones.java') |
        ForEach-Object { Join-Path $deathRoot $_ }
    $sources += (Join-Path $repository 'tests/DeathPointMetadataTest.java'), (Join-Path $repository 'tests/DeathPointDeletionTest.java')
}
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $gsonJar.FullName -d $outputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Waypoint metadata test compilation failed.' }
$classpath = "$outputDirectory;$($gsonJar.FullName)"
& (Join-Path $JdkHome 'bin/java.exe') "-Dcodex.waypoint.test.root=$fixtureRoot" -cp $classpath net.aosankaku.openmapdelta.marker.WaypointMetadataTest
if ($LASTEXITCODE -ne 0) { throw 'Waypoint metadata tests failed.' }
if ($VersionRoot -in @('1.20.1', '1.21.1', '26.2')) {
    & (Join-Path $JdkHome 'bin/java.exe') "-Dcodex.waypoint.test.root=$fixtureRoot" -cp $classpath net.aosankaku.openmapdelta.marker.DeathPointMetadataTest
    if ($LASTEXITCODE -ne 0) { throw 'Deathpoint metadata tests failed.' }
    & (Join-Path $JdkHome 'bin/java.exe') "-Dcodex.waypoint.test.root=$fixtureRoot" -cp $classpath net.aosankaku.openmapdelta.death.DeathPointDeletionTest
    if ($LASTEXITCODE -ne 0) { throw 'Deathpoint deletion persistence tests failed.' }
}
