# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string[]]$VersionRoot = @('1.20.1', '1.21.1', '26.1.2', '26.2'),
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { $JdkHome = 'C:\Program Files\Java\jdk-17' }
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw 'JdkHome must contain bin/javac.exe and bin/java.exe.'
}

$commonStubs = @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/map-selection-screen-stubs/common') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName })
$test = Join-Path $repository 'tests/MapModSelectionScreenRenderTest.java'
foreach ($root in $VersionRoot) {
    $variant = if ($root -eq '1.20.1') { 'legacy' } elseif ($root -eq '1.21.1') { 'render' } else { 'extract' }
    $versionStubs = @(Get-ChildItem -LiteralPath (Join-Path $repository "tests/map-selection-screen-stubs/$variant") -Recurse -Filter '*.java' |
        ForEach-Object { $_.FullName })
    $source = Join-Path $repository "$root/common/src/main/java/net/aosankaku/openmapdelta/gui/MapModSelectionScreen.java"
    $output = Join-Path $repository ".task-work/map-selection-blur/$root"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $compileLog = Join-Path $output 'compile.log'
    & $javac '-J-Dfile.encoding=UTF-8' '--release' '17' '-encoding' 'UTF-8' '-d' $output `
        $commonStubs $versionStubs $source $test *> $compileLog
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $compileLog -Tail 60
        throw "Map selection screen test compilation failed for $root."
    }
    & $java '-Dfile.encoding=UTF-8' "-Dmap.selection.version=$root" '-cp' $output `
        net.aosankaku.openmapdelta.gui.MapModSelectionScreenRenderTest
    if ($LASTEXITCODE -ne 0) { throw "Map selection screen render test failed for $root." }
}
