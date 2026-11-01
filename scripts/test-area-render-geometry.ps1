# Compiles the real, dependency-free fullscreen area scanline projection helpers and pixel-space tests.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }

$testOutput = Join-Path $repository '26.2/common/build/area-render-geometry-tests'
$areaSource = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/area'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$sources = @(
    (Join-Path $areaSource 'MapArea.java'),
    (Join-Path $areaSource 'AreaDocument.java'),
    (Join-Path $areaSource 'AreaGeometryOps.java'),
    (Join-Path $areaSource 'AreaRenderGeometry.java'),
    (Join-Path $PSScriptRoot '../tests/AreaRenderGeometryTest.java')
)
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $testOutput @sources
if ($LASTEXITCODE -ne 0) { throw 'Area render geometry test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $testOutput net.aosankaku.openmapdelta.area.AreaRenderGeometryTest
if ($LASTEXITCODE -ne 0) { throw 'Area render geometry tests failed.' }
