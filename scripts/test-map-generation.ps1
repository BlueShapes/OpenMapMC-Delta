# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')][string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$output = Join-Path $repository "$VersionRoot/common/build/map-generation-tests"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$names = @('world/ChunkColumnScan.java', 'world/BoundedMapExecutor.java', 'world/ChunkScanQueue.java', 'color/BiomeGrassColor.java')
foreach ($root in @('1.20.1', '1.21.1', '26.1.2', '26.2')) {
    foreach ($name in $names) {
        $reference = (Get-FileHash -LiteralPath (Join-Path $javaRoot $name)).Hash
        $other = (Get-FileHash -LiteralPath (Join-Path $repository "$root/common/src/main/java/net/aosankaku/openmapdelta/$name")).Hash
        if ($reference -ne $other) { throw "Pure map generation helper differs: $root/$name" }
    }
}
$sources = @($names | ForEach-Object { Join-Path $javaRoot $_ }) + @(
    (Join-Path $repository 'tests/MapGenerationTest.java'), (Join-Path $repository 'tests/ChunkScanQueueTest.java'))
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Map generation test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.world.MapGenerationTest
if ($LASTEXITCODE -ne 0) { throw 'Map generation tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.world.ChunkScanQueueTest
if ($LASTEXITCODE -ne 0) { throw 'Chunk scan queue tests failed.' }
