# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1','1.21.1','26.1.2','26.2','all')][string]$VersionRoot='26.2',
    [string]$JdkHome=$env:JAVA_HOME
)
$ErrorActionPreference='Stop'
$repository=Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$versions=if ($VersionRoot -eq 'all') { @('1.20.1','1.21.1','26.1.2','26.2') } else { @($VersionRoot) }
foreach ($version in $versions) {
    $source=Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta/gui"
    $output=Join-Path $repository "$version/common/build/fullscreen-controller-tests"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $log=Join-Path $output 'compile.log'
    & (Join-Path $JdkHome 'bin/javac.exe') '-J-Duser.language=en' '-J-Dfile.encoding=UTF-8' --release 17 -encoding UTF-8 -d $output `
        (Join-Path $source 'FullscreenControllerPolicy.java') (Join-Path $source 'ControllerNavigationLayout.java') `
        (Join-Path $repository 'tests/FullscreenControllerPolicyTest.java') *> $log
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 12; throw "Fullscreen controller compile failed: $version" }
    & (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.gui.FullscreenControllerPolicyTest
    if ($LASTEXITCODE -ne 0) { throw "Fullscreen controller tests failed: $version" }
}
