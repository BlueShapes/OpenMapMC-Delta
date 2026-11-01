# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GsonJarPath
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
if (!$GsonJarPath) {
    $gsonRoot = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
    if (Test-Path $gsonRoot) {
        $GsonJarPath = Get-ChildItem -Path $gsonRoot -Directory |
            Sort-Object { [version]$_.Name } -Descending |
            ForEach-Object { Get-ChildItem -Path $_.FullName -Filter 'gson-*.jar' -File -Recurse | Select-Object -First 1 } |
            Select-Object -First 1 -ExpandProperty FullName
    }
}
if (!$GsonJarPath -or !(Test-Path $GsonJarPath)) { throw 'A cached Gson jar is required; pass -GsonJarPath.' }
$sharedRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/shared"
$markerRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker"
$platformRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/platform"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/shared-waypoint-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $GsonJarPath -d $outputDirectory `
    (Join-Path $markerRoot 'MarkerIconId.java') `
    (Join-Path $markerRoot 'WaypointFileStore.java') `
    (Join-Path $platformRoot 'DataDirectoryMigration.java') `
    (Join-Path $platformRoot 'DataPaths.java') `
    (Join-Path $sharedRoot 'SharedWaypointData.java') `
    (Join-Path $sharedRoot 'SharedWaypoint.java') `
    (Join-Path $sharedRoot 'SharedWaypointSnapshot.java') `
    (Join-Path $sharedRoot 'SharedWaypointSnapshotAssembler.java') `
    (Join-Path $sharedRoot 'SharedWaypointRequest.java') `
    (Join-Path $sharedRoot 'SharedWaypointFeedback.java') `
    (Join-Path $sharedRoot 'SharedWaypointClient.java') `
    (Join-Path $sharedRoot 'SharedWaypointPreferences.java') `
    (Join-Path $repository 'tests/shared-client-stubs/net/minecraft/network/chat/Component.java') `
    (Join-Path $repository 'tests/shared-client-stubs/net/minecraft/client/Minecraft.java') `
    (Join-Path $repository 'tests/shared-client-stubs/net/aosankaku/openmapdelta/platform/Services.java') `
    (Join-Path $repository 'tests/shared-client-stubs/org/slf4j/Logger.java') `
    (Join-Path $repository 'tests/shared-client-stubs/org/slf4j/LoggerFactory.java') `
    (Join-Path $repository 'tests/SharedWaypointSnapshotAssemblerTest.java') `
    (Join-Path $repository 'tests/SharedWaypointClientTest.java') `
    (Join-Path $repository 'tests/SharedWaypointPreferencesTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Shared waypoint test compilation failed.' }
$testClasspath = "$outputDirectory;$GsonJarPath"
& (Join-Path $JdkHome 'bin/java.exe') -cp $testClasspath net.aosankaku.openmapdelta.shared.SharedWaypointSnapshotAssemblerTest
if ($LASTEXITCODE -ne 0) { throw 'Shared waypoint tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $testClasspath net.aosankaku.openmapdelta.shared.SharedWaypointClientTest
if ($LASTEXITCODE -ne 0) { throw 'Shared waypoint client tests failed.' }
$preferenceTestDirectory = Join-Path $outputDirectory "preference-test-$([guid]::NewGuid().ToString())"
New-Item -ItemType Directory -Path $preferenceTestDirectory | Out-Null
& (Join-Path $JdkHome 'bin/java.exe') -cp $testClasspath net.aosankaku.openmapdelta.shared.SharedWaypointPreferencesTest write $preferenceTestDirectory
if ($LASTEXITCODE -ne 0) { throw 'Shared waypoint preference persistence tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $testClasspath net.aosankaku.openmapdelta.shared.SharedWaypointPreferencesTest verify $preferenceTestDirectory
if ($LASTEXITCODE -ne 0) { throw 'Shared waypoint preference reload tests failed.' }
