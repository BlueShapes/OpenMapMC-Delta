# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1','1.21.1','26.1.2','26.2')][string]$VersionRoot='1.21.1',
    [string]$JdkHome=$env:JAVA_HOME
)
$ErrorActionPreference='Stop'
$repository=Split-Path -Parent $PSScriptRoot
if(!$JdkHome){throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).'}
$relative='common/src/main/java/net/aosankaku/openmapdelta/render/MinimapLayerTransition.java'
$source=Join-Path $repository "$VersionRoot/$relative"
$output=Join-Path $repository '.task-work/minimap-layer-transition-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output $source (Join-Path $repository 'tests/MinimapLayerTransitionTest.java')
if($LASTEXITCODE -ne 0){throw 'Minimap layer transition compilation failed.'}
foreach($version in @('1.20.1','1.21.1','26.1.2','26.2')){
    if((Get-FileHash -LiteralPath (Join-Path $repository "$version/$relative")).Hash -ne (Get-FileHash -LiteralPath $source).Hash){throw "Transition helper differs: $version"}
}
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.render.MinimapLayerTransitionTest
if($LASTEXITCODE -ne 0){throw 'Minimap layer transition tests failed.'}
