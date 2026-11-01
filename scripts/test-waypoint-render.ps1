# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$renderDirectory = "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/render"
$names = @('WaypointRenderMath.java', 'WaypointClustering.java', 'WaypointClusterBadge.java', 'WaypointClusterOrder.java', 'WaypointClusterLabels.java')
$helpers = @($names | ForEach-Object { Join-Path $repository "$renderDirectory/$_" })
foreach ($root in @('1.20.1', '1.21.1', '26.1.2', '26.2')) {
    foreach ($name in $names) {
        # 1.20.1 and 1.21.1 additionally support Smart Deathpoint beacon pulses.
        $rootHasDeathpoints = $root -in @('1.20.1', '1.21.1', '26.2')
        $referenceHasDeathpoints = $VersionRoot -in @('1.20.1', '1.21.1', '26.2')
        if ($name -eq 'WaypointRenderMath.java' -and ($rootHasDeathpoints -ne $referenceHasDeathpoints)) { continue }
        $reference = (Get-FileHash -LiteralPath (Join-Path $repository "$renderDirectory/$name")).Hash
        $other = (Get-FileHash -LiteralPath (Join-Path $repository "$root/common/src/main/java/net/aosankaku/openmapdelta/render/$name")).Hash
        if ($reference -ne $other) { throw "Waypoint render helper differs: $root/$name" }
    }
}
$tests = @((Join-Path $repository 'tests/WaypointRenderMathTest.java'), (Join-Path $repository 'tests/WaypointClusteringTest.java'), (Join-Path $repository 'tests/WaypointClusterOrderTest.java'), (Join-Path $repository 'tests/WaypointClusterLabelsTest.java'))
if ($VersionRoot -in @('1.20.1', '1.21.1', '26.2')) { $tests += (Join-Path $repository 'tests/DeathPointRenderingTest.java') }
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/waypoint-render-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory $helpers $tests
if ($LASTEXITCODE -ne 0) { throw 'Waypoint render math test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.WaypointRenderMathTest
if ($LASTEXITCODE -ne 0) { throw 'Waypoint render math tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.WaypointClusteringTest
if ($LASTEXITCODE -ne 0) { throw 'Waypoint clustering tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.WaypointClusterOrderTest
if ($LASTEXITCODE -ne 0) { throw 'Waypoint cluster order tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.WaypointClusterLabelsTest
if ($LASTEXITCODE -ne 0) { throw 'Waypoint cluster label tests failed.' }
if ($VersionRoot -in @('1.20.1', '1.21.1', '26.2')) {
    & (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.DeathPointRenderingTest
    if ($LASTEXITCODE -ne 0) { throw 'Deathpoint beacon tests failed.' }
}
