# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
$gson = Get-ChildItem -Path $cache -Filter 'gson-*.jar' -Recurse | Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' } | Sort-Object FullName -Descending | Select-Object -First 1
if (!$gson) { throw 'Gson was not found in the local Gradle dependency cache.' }
$shared = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/shared"
$marker = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker"
$output = Join-Path $repository "$VersionRoot/common/build/shared-waypoint-server-tests"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @(
    (Join-Path $marker 'MarkerIconId.java'),
    (Join-Path $marker 'WaypointFileStore.java'),
    (Join-Path $shared 'SharedWaypointData.java'),
    (Join-Path $shared 'SharedWaypoint.java'),
    (Join-Path $shared 'SharedWaypointAccess.java'),
    (Join-Path $shared 'SharedWaypointRequest.java'),
    (Join-Path $shared 'SharedWaypointSnapshot.java'),
    (Join-Path $shared 'SharedWaypointFeedback.java'),
    (Join-Path $shared 'SharedWaypointStore.java'),
    (Join-Path $PSScriptRoot '../tests/SharedWaypointProtocolStoreTest.java')
)
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $gson.FullName -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Shared waypoint server test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp "$output;$($gson.FullName)" net.aosankaku.openmapdelta.shared.SharedWaypointProtocolStoreTest
if ($LASTEXITCODE -ne 0) { throw 'Shared waypoint server tests failed.' }
