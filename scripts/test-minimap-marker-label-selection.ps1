# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$helper = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker/MinimapMarkerLabelSelection.java"
$labels = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker/MinimapWaypointLabel.java"
$layout = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker/MinimapWaypointLayout.java"
$tests = Join-Path $repository 'tests/MinimapMarkerLabelSelectionTest.java'
$layoutTests = Join-Path $repository 'tests/MinimapWaypointLayoutTest.java'
foreach ($root in @('1.20.1', '1.21.1', '26.1.2', '26.2')) {
    foreach ($name in @('MinimapWaypointLayout.java', 'MinimapWaypointLabel.java')) {
        $reference = (Get-FileHash -LiteralPath (Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker/$name")).Hash
        $other = (Get-FileHash -LiteralPath (Join-Path $repository "$root/common/src/main/java/net/aosankaku/openmapdelta/marker/$name")).Hash
        if ($reference -ne $other) { throw "Minimap waypoint helper differs: $root/$name" }
    }
}
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/minimap-marker-label-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $helper $labels $layout $tests $layoutTests
if ($LASTEXITCODE -ne 0) { throw 'Minimap marker label selection test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.marker.MinimapMarkerLabelSelectionTest
if ($LASTEXITCODE -ne 0) { throw 'Minimap marker label selection tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.marker.MinimapWaypointLayoutTest
if ($LASTEXITCODE -ne 0) { throw 'Minimap waypoint layout tests failed.' }
