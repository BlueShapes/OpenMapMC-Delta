# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
foreach ($name in @('FullscreenMapViewport', 'FullscreenChunkGrid', 'FullscreenMapZoom', 'FullscreenMapProjection', 'FullscreenMapMarkerGeometry', 'FullscreenMapLabelGeometry')) {
    $referenceHash = $null
    foreach ($version in @('1.20.1', '1.21.1', '26.1.2', '26.2')) {
        # 26.2 extends detail zoom to 4x; the older roots retain their 2x policy.
        if ($name -eq 'FullscreenMapZoom' -and $version -eq '26.2') { continue }
        $source = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta/render/$name.java"
        $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
        if ($null -eq $referenceHash) { $referenceHash = $hash }
        elseif ($hash -ne $referenceHash) { throw "${version}: $name geometry differs across roots" }
    }
}
$helper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/render/FullscreenMapViewport.java"
$gridHelper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/render/FullscreenChunkGrid.java"
$zoomHelper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/render/FullscreenMapZoom.java"
$projectionHelper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/render/FullscreenMapProjection.java"
$markerGeometryHelper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/render/FullscreenMapMarkerGeometry.java"
$labelGeometryHelper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/render/FullscreenMapLabelGeometry.java"
$labelGeometryTests = Join-Path $repository 'tests/FullscreenMapLabelGeometryTest.java'
$zoomTests = Join-Path $repository 'tests/FullscreenMapZoomTest.java'
$tests = Join-Path $repository 'tests/FullscreenMapViewportTest.java'
$tileHelper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/world/Tile.java"
$reliefHelper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/world/TerrainRelief.java"
$averageHelper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/world/TileColorAverage.java"
$averageTests = Join-Path $repository "tests/TerrainPixelAverageTest.java"
$gridTests = Join-Path $repository 'tests/FullscreenChunkGridTest.java'
$rasterHelper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/render/FullscreenTerrainRaster.java"
$rasterTests = Join-Path $repository 'tests/FullscreenTerrainRasterTest.java'
$reliefTests = Join-Path $repository 'tests/TerrainReliefTest.java'
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/fullscreen-map-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $helper $gridHelper $zoomHelper $projectionHelper $markerGeometryHelper $labelGeometryHelper $tileHelper $reliefHelper $averageHelper $rasterHelper $tests $gridTests $zoomTests $labelGeometryTests $averageTests $rasterTests $reliefTests
if ($LASTEXITCODE -ne 0) { throw 'Fullscreen map viewport test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.FullscreenMapViewportTest
if ($LASTEXITCODE -ne 0) { throw 'Fullscreen map viewport tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.FullscreenChunkGridTest
if ($LASTEXITCODE -ne 0) { throw 'Fullscreen chunk grid tests failed.' }
$minimumBpp = if ($VersionRoot -eq '26.2') { '0.25' } else { '0.5' }
& (Join-Path $JdkHome 'bin/java.exe') "-Dfullscreen.test.minBpp=$minimumBpp" -cp $outputDirectory net.aosankaku.openmapdelta.render.FullscreenMapZoomTest
if ($LASTEXITCODE -ne 0) { throw 'Fullscreen zoom and projection tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.FullscreenMapLabelGeometryTest
if ($LASTEXITCODE -ne 0) { throw 'Fullscreen label geometry tests failed.' }

& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.TerrainPixelAverageTest
if ($LASTEXITCODE -ne 0) { throw 'Terrain averaging tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.FullscreenTerrainRasterTest
if ($LASTEXITCODE -ne 0) { throw 'Fullscreen terrain raster tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.world.TerrainReliefTest
if ($LASTEXITCODE -ne 0) { throw 'Terrain relief tests failed.' }
