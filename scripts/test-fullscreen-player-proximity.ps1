# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$sourceRoot = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta'
$output = Join-Path $repository '26.2/common/build/fullscreen-player-proximity-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @(
    'marker/FullscreenLocalPlayerSizing.java', 'marker/PlayerMarkerSizing.java', 'marker/MarkerIconSizing.java',
    'render/FullscreenMapZoom.java', 'render/FullscreenMapProjection.java',
    'render/FullscreenMapViewport.java', 'render/FullscreenChunkGrid.java'
) | ForEach-Object { Join-Path $sourceRoot $_ }
$sources += Join-Path $repository 'tests/FullscreenLocalPlayerSizingTest.java'
$compileLog = Join-Path $output 'compile.log'
& (Join-Path $JdkHome 'bin/javac.exe') '-J-Duser.language=en' '-J-Dfile.encoding=UTF-8' --release 17 -encoding UTF-8 -d $output @sources *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 15
    throw 'Fullscreen self head tests failed to compile.'
}
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.marker.FullscreenLocalPlayerSizingTest
if ($LASTEXITCODE -ne 0) { throw 'Fullscreen self head tests failed.' }
