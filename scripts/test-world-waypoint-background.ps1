# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$sourceRoot = Join-Path $repository '1.21.1/common/src/main'
$renderRoot = Join-Path $sourceRoot 'java/net/aosankaku/openmapdelta/render'
$outputDirectory = Join-Path $repository '1.21.1/common/build/world-waypoint-background-tests'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$stubs = @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/world-waypoint-background-stubs') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources = @('WorldWaypointBackground.java','WaypointRenderMath.java','WaypointClusterBadge.java') | ForEach-Object { Join-Path $renderRoot $_ }
$compileLog = Join-Path $outputDirectory 'compile.log'
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $stubs $sources (Join-Path $repository 'tests/WorldWaypointBackgroundTest.java') *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 30
    throw 'World waypoint background test compilation failed.'
}
$texture = Join-Path $sourceRoot 'resources/assets/openmapdelta/textures/misc/waypoint_background.png'
& (Join-Path $JdkHome 'bin/java.exe') '-Djava.awt.headless=true' "-Dwaypoint.background.texture=$texture" -cp $outputDirectory net.aosankaku.openmapdelta.render.WorldWaypointBackgroundTest
if ($LASTEXITCODE -ne 0) { throw 'World waypoint background tests failed.' }
