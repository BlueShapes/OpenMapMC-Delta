# Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.2')][string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$output = Join-Path $repository "$VersionRoot/common/build/server-config-tests"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$gson = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson') -Filter 'gson-*.jar' -Recurse |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object FullName -Descending | Select-Object -First 1
if (!$gson) { throw 'Cached Gson dependency is required.' }
$sources = @(
    'server/ServerConfig.java', 'server/ServerConfigValues.java', 'server/ServerConfigRequest.java',
    'server/ServerConfigSnapshot.java', 'server/ServerConfigService.java', 'server/ServerWebMapNotice.java',
    'web/WebMapUrls.java', 'marker/WaypointFileStore.java', 'marker/MarkerIconId.java',
    'shared/SharedWaypointServer.java', 'shared/SharedWaypointStore.java', 'shared/SharedWaypointData.java',
    'shared/SharedWaypoint.java', 'shared/SharedWaypointAccess.java', 'shared/SharedWaypointRequest.java',
    'shared/SharedWaypointSnapshot.java', 'shared/SharedWaypointFeedback.java'
) | ForEach-Object { Join-Path $source $_ }
$sources += @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/server-config-stubs') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
$sources += Join-Path $repository 'tests/ServerConfigTest.java'
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $gson.FullName -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Server config test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') "-Djava.io.tmpdir=$output" -cp "$output;$($gson.FullName)" net.aosankaku.openmapdelta.server.ServerConfigTest
if ($LASTEXITCODE -ne 0) { throw 'Server config tests failed.' }
