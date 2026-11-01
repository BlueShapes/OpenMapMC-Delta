# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GsonJar,
    [ValidateSet('all', 'parent-auth', 'personal-store', 'zero-uuid-store', 'rate-limit', 'rate-limit-malformed', 'disk-first')]
    [string]$Case = 'all'
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
if (!$GsonJar) {
    $gsonCache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
    $candidate = Get-ChildItem -LiteralPath $gsonCache -Recurse -Filter 'gson-2.14.0.jar' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (!$candidate) { throw 'Gson 2.14.0 was not found in the Gradle cache; pass -GsonJar explicitly.' }
    $GsonJar = $candidate.FullName
}
$areaRoot = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/area'
$stubRoot = Join-Path $repository 'tests/area-server-stubs'
$output = Join-Path $repository '26.2/common/build/area-server-tests'
$task = Join-Path $repository '.task-work/areas-20261028'
$testRoot = Join-Path $task 'server-worlds'
New-Item -ItemType Directory -Force -Path $output, $task, $testRoot | Out-Null
$stubs = @(Get-ChildItem -LiteralPath $stubRoot -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
$sources = @(
    (Join-Path $areaRoot 'AreaGeometryOps.java'),
    (Join-Path $areaRoot 'MapArea.java'),
    (Join-Path $areaRoot 'AreaDocument.java'),
    (Join-Path $areaRoot 'AreaWire.java'),
    (Join-Path $areaRoot 'AreaTransactions.java'),
    (Join-Path $areaRoot 'AreaStore.java'),
    (Join-Path $areaRoot 'AreaServer.java'),
    (Join-Path $PSScriptRoot '../tests/AreaServerTest.java')
)
$compileOutput = & (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $GsonJar -d $output ($stubs + $sources) 2>&1
if ($LASTEXITCODE -ne 0) {
    $compileOutput | Set-Content -LiteralPath (Join-Path $task 'area-server-compile.log') -Encoding utf8
    $compileOutput
    throw 'Area server test compilation failed.'
}
$runtimeClasspath = "$output;$GsonJar"
& (Join-Path $JdkHome 'bin/java.exe') "-DareaTestRoot=$testRoot" -cp $runtimeClasspath net.aosankaku.openmapdelta.area.AreaServerTest $Case
if ($LASTEXITCODE -ne 0) { throw "Area server tests failed for case '$Case'." }
