# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$versions = @('1.20.1', '1.21.1', '26.1.2', '26.2')
$referenceHash = $null
foreach ($version in $versions) {
    $source = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta/render/FullscreenRailGeometry.java"
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if ($null -eq $referenceHash) { $referenceHash = $hash }
    elseif ($hash -ne $referenceHash) { throw "${version}: fullscreen rail geometry differs across roots" }
}

$sourceRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/fullscreen-rail-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @(
    (Join-Path $sourceRoot 'compat/TransitRail.java'),
    (Join-Path $sourceRoot 'render/FullscreenMapZoom.java'),
    (Join-Path $sourceRoot 'render/FullscreenRailGeometry.java'),
    (Join-Path $repository 'tests/FullscreenRailGeometryTest.java')
)
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $sources
if ($LASTEXITCODE -ne 0) { throw 'Fullscreen rail geometry compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.FullscreenRailGeometryTest
if ($LASTEXITCODE -ne 0) { throw 'Fullscreen rail geometry tests failed.' }
