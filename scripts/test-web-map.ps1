# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1','1.21.1','26.1.2','26.2')][string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GsonJar,
    [switch]$CompileOnly
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome.' }
if (!$GsonJar) {
    $GsonJar = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson') -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
}
if (!$GsonJar) { throw 'Pass -GsonJar or build the project once to cache Gson.' }
$source = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/web"
$output = Join-Path $repository "$VersionRoot/common/build/web-map-tests"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$files = 'WebMapAuth.java','WebMapPng.java','WebMapSnapshot.java','WebMapImagePixels.java','WebMapIconCache.java','WebMapSettings.java','WebMapServer.java','WebMapUrls.java' | ForEach-Object { Join-Path $source $_ }
if ($VersionRoot -like '26.*') { $files += Join-Path $repository 'tests/WebMapAsyncIconCacheTest.java' }
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $GsonJar -d $output @files (Join-Path $repository 'tests/WebMapTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Web map test compilation failed.' }
$classpath = "$output;$GsonJar;$(Join-Path $repository "$VersionRoot/common/src/main/resources")"
if ($CompileOnly) { Write-Output $classpath; return }
& (Join-Path $JdkHome 'bin/java.exe') -cp $classpath net.aosankaku.openmapdelta.web.WebMapTest
if ($LASTEXITCODE -ne 0) { throw 'Web map tests failed.' }
if ($VersionRoot -like '26.*') {
    & (Join-Path $JdkHome 'bin/java.exe') -cp $classpath net.aosankaku.openmapdelta.web.WebMapAsyncIconCacheTest
    if ($LASTEXITCODE -ne 0) { throw 'Asynchronous icon cache tests failed.' }
}
