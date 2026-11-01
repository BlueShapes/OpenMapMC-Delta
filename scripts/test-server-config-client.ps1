# Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.2')][string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$output = Join-Path $repository "$VersionRoot/common/build/server-config-client-tests"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @(
    'client/ServerConfigClient.java', 'server/ServerConfigRequest.java', 'server/ServerConfigSnapshot.java',
    'server/ServerConfigValues.java', 'web/WebMapUrls.java'
) | ForEach-Object { Join-Path $source $_ }
$sources += @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/server-config-client-stubs') -Recurse -Filter '*.java' |
    Select-Object -ExpandProperty FullName)
$sources += Join-Path $repository 'tests/ServerConfigClientTest.java'
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Server config client test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') "-Djava.io.tmpdir=$output" -cp $output net.aosankaku.openmapdelta.client.ServerConfigClientTest
if ($LASTEXITCODE -ne 0) { throw 'Server config client tests failed.' }
