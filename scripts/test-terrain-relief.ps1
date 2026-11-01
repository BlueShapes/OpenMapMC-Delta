# Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2', 'all')]
    [string]$VersionRoot = 'all',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { $JdkHome = 'C:\Program Files\Java\jdk-17' }
$versions = if ($VersionRoot -eq 'all') { @('1.20.1', '1.21.1', '26.1.2', '26.2') } else { @($VersionRoot) }
$referenceHash = $null
foreach ($version in $versions) {
    $common = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta"
    $tile = Join-Path $common 'world/Tile.java'
    $relief = Join-Path $common 'world/TerrainRelief.java'
    $test = Join-Path $repository 'tests/TerrainReliefTest.java'
    $output = Join-Path $repository ".task-work/terrain-relief-tests/$version"
    New-Item -ItemType Directory -Force -Path $output | Out-Null

    & (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output $tile $relief $test
    if ($LASTEXITCODE -ne 0) { throw "$version terrain relief test compilation failed." }
    & (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.world.TerrainReliefTest
    if ($LASTEXITCODE -ne 0) { throw "$version terrain relief tests failed." }

    $hash = (Get-FileHash -LiteralPath $relief -Algorithm SHA256).Hash
    if ($null -eq $referenceHash) { $referenceHash = $hash }
    elseif ($hash -ne $referenceHash) { throw "$version TerrainRelief implementation differs across roots." }
}
Write-Output "Verified height relief policy for $($versions.Count) version root(s)."
