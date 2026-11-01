# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome.' }
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path $repository '1.20.1/common/build/fullscreen-chrome-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$reference = Join-Path $repository '1.20.1/common/src/main/java/net/aosankaku/openmapdelta/gui/FullscreenMapChrome.java'
foreach ($root in @('1.21.1','26.1.2','26.2')) {
    $other = Join-Path $repository "$root/common/src/main/java/net/aosankaku/openmapdelta/gui/FullscreenMapChrome.java"
    if ((Get-FileHash -LiteralPath $reference).Hash -ne (Get-FileHash -LiteralPath $other).Hash) { throw "F1 geometry differs: $root" }
}
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output $reference (Join-Path $repository 'tests/FullscreenMapChromeTest.java')
if ($LASTEXITCODE -ne 0) { throw 'F1 geometry test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.gui.FullscreenMapChromeTest
if ($LASTEXITCODE -ne 0) { throw 'F1 geometry tests failed.' }
