# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$referenceHash = $null
foreach ($version in @('1.20.1', '1.21.1', '26.1.2', '26.2')) {
    $source = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta/gui/ContextMenuPlacement.java"
    $hash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
    if ($null -eq $referenceHash) { $referenceHash = $hash; $referenceSource = $source }
    elseif ($hash -ne $referenceHash) { throw "${version}: popup placement differs across roots" }
}
$outputDirectory = Join-Path $repository '.task-work/context-menu-tests'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    $referenceSource (Join-Path $repository 'tests/ContextMenuPlacementTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Context menu test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.gui.ContextMenuPlacementTest
if ($LASTEXITCODE -ne 0) { throw 'Context menu tests failed.' }
