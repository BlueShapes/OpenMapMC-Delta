# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GsonJar
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
if (!$GsonJar) {
    $gradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $gsonCache = Join-Path $gradleCache 'caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1'
    $GsonJar = Get-ChildItem -LiteralPath $gsonCache -Recurse -Filter 'gson-2.10.1.jar' |
        Select-Object -First 1 -ExpandProperty FullName
}
if (!$GsonJar -or !(Test-Path -LiteralPath $GsonJar)) { throw 'Pass -GsonJar with a Gson 2.10.1+ jar.' }
$reader = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker/WaypointImportReader.java"
$tests = Join-Path $repository 'tests/WaypointImportReaderTest.java'
$logicTests = Join-Path $repository 'tests/WaypointImportLogicTest.java'
$logicSources = @('WaypointImportPolicy.java', 'WaypointImportSession.java', 'WaypointFileStore.java') |
    ForEach-Object { Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker/$_" }
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/waypoint-import-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $GsonJar -d $outputDirectory $reader $logicSources $tests $logicTests
if ($LASTEXITCODE -ne 0) { throw 'Waypoint import test compilation failed.' }
$classPath = $outputDirectory + [IO.Path]::PathSeparator + $GsonJar
& (Join-Path $JdkHome 'bin/java.exe') -cp $classPath net.aosankaku.openmapdelta.marker.WaypointImportReaderTest
if ($LASTEXITCODE -ne 0) { throw 'Waypoint import tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $classPath net.aosankaku.openmapdelta.marker.WaypointImportLogicTest
if ($LASTEXITCODE -ne 0) { throw 'Waypoint import logic tests failed.' }
