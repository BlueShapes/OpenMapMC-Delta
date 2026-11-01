# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2', 'all')]
    [string]$VersionRoot = 'all',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$versions = if ($VersionRoot -eq 'all') { @('1.20.1','1.21.1','26.1.2','26.2') } else { @($VersionRoot) }
$referenceHash = $null
foreach ($version in $versions) {
    $source = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta/world/CaveLayerSteps.java"
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if ($null -eq $referenceHash) { $referenceHash = $hash; $referenceSource = $source }
    elseif ($hash -ne $referenceHash) { throw "${version}: cave layer helper differs across roots" }
}
$outputDirectory = Join-Path $repository '.task-work/cave-layer-tests'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    $referenceSource (Join-Path $repository 'tests/CaveLayerStepsTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Cave layer test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.world.CaveLayerStepsTest
if ($LASTEXITCODE -ne 0) { throw 'Cave layer tests failed.' }
Write-Output "Verified $($versions.Count) identical version-root helpers."
