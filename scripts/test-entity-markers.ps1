# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.21.1',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw 'JdkHome must contain bin/javac.exe and bin/java.exe.'
}

$variant = if ($VersionRoot -in @('1.20.1', '1.21.1')) { 'legacy' } else { 'modern' }
$stubRoot = Join-Path $repository 'tests/entity-marker-stubs'
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/entity-marker-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java"
$markerRoot = Join-Path $javaRoot 'net/aosankaku/openmapdelta/marker'
$sources = @(
    (Join-Path $markerRoot 'MarkerIconRenderer.java')
    (Join-Path $stubRoot 'common/net/aosankaku/openmapdelta/marker/Marker.java')
    (Join-Path $stubRoot 'common/net/aosankaku/openmapdelta/marker/MarkerHoverStyle.java')
    (Join-Path $stubRoot "$variant/net/aosankaku/openmapdelta/marker/MarkerIconCatalog.java")
    (Join-Path $stubRoot "$variant/net/aosankaku/openmapdelta/marker/EntityMarkerRenderFixture.java")
    (Join-Path $stubRoot 'common/net/aosankaku/openmapdelta/util/RenderUtil.java')
    (Join-Path $repository 'tests/EntityMarkerStyleTest.java')
    (Join-Path $repository 'tests/EntityMarkerRenderTest.java')
)
$entityMarkerStyle = Join-Path $markerRoot 'EntityMarkerStyle.java'
if ($VersionRoot -eq '26.2') {
    $sources += Join-Path $stubRoot 'modern/net/aosankaku/openmapdelta/marker/EntityIconSprites.java'
}
if (Test-Path -LiteralPath $entityMarkerStyle) { $sources += $entityMarkerStyle }
$sources += Get-ChildItem -LiteralPath (Join-Path $stubRoot 'common') -File -Recurse |
    ForEach-Object FullName
$compileLog = Join-Path $outputDirectory 'compile.log'
& $javac '-J-Dfile.encoding=UTF-8' '-J-Duser.language=en' '--release' '17' '-encoding' 'UTF-8' '-d' $outputDirectory @sources *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -TotalCount 24
    throw "Entity marker tests failed to compile for $VersionRoot."
}

foreach ($testClass in @(
    'net.aosankaku.openmapdelta.marker.EntityMarkerStyleTest'
    'net.aosankaku.openmapdelta.marker.EntityMarkerRenderTest'
)) {
    & $java '-cp' $outputDirectory $testClass
    if ($LASTEXITCODE -ne 0) { throw "Entity marker test failed: $testClass" }
}
