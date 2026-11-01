# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')][string]$VersionRoot = '1.21.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$output = Join-Path $repository '.task-work/minimap-layer-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
foreach ($relative in @('config/MinimapLayerMode.java', 'world/CaveLayerSteps.java', 'world/MinimapLayers.java')) {
    $reference = (Get-FileHash -LiteralPath (Join-Path $javaRoot $relative)).Hash
    foreach ($version in @('1.20.1', '1.21.1', '26.1.2', '26.2')) {
        $other = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta/$relative"
        if ((Get-FileHash -LiteralPath $other).Hash -ne $reference) { throw "Layer helper differs: $version/$relative" }
    }
}
$sources = @(
    (Join-Path $javaRoot 'config/MinimapLayerMode.java'),
    (Join-Path $javaRoot 'world/CaveLayerSteps.java'),
    (Join-Path $javaRoot 'world/MinimapLayers.java'),
    (Join-Path $repository 'tests/MinimapLayersTest.java')
)
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Minimap layer test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.world.MinimapLayersTest
if ($LASTEXITCODE -ne 0) { throw 'Minimap layer tests failed.' }
