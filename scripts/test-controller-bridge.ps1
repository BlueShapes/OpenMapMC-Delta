# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$referenceHash = $null
foreach ($version in @('1.20.1', '1.21.1', '26.1.2', '26.2')) {
    $bridge = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta/gui/ControllerNavigationBridge.java"
    $layout = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta/gui/ControllerNavigationLayout.java"
    $hash = (Get-FileHash -LiteralPath $bridge -Algorithm SHA256).Hash + ':' + (Get-FileHash -LiteralPath $layout -Algorithm SHA256).Hash
    if ($null -eq $referenceHash) { $referenceHash = $hash; $referenceBridge = $bridge; $referenceLayout = $layout }
    elseif ($hash -ne $referenceHash) { throw "${version}: controller navigation helpers differ across roots" }
}
$outputDirectory = Join-Path $repository '.task-work/controller-bridge-tests'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    $referenceLayout $referenceBridge (Join-Path $repository 'tests/ControllerNavigationBridgeTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Controller navigation bridge test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.gui.ControllerNavigationBridgeTest
if ($LASTEXITCODE -ne 0) { throw 'Controller navigation bridge tests failed.' }
