# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string[]]$VersionRoot = @('1.20.1', '1.21.1', '26.1.2', '26.2'),
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$SourceOverride
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { $JdkHome = 'C:\Program Files\Java\jdk-17' }
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw 'JdkHome must contain bin/javac.exe and bin/java.exe.'
}
if ($SourceOverride -and $VersionRoot.Count -ne 1) {
    throw 'SourceOverride can only be used with a single VersionRoot.'
}

$commonStubs = @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/map-selection-screen-stubs/common') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName })
$editorStubs = @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/waypoint-edit-screen-stubs/common') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName })
$test = Join-Path $repository 'tests/WaypointEditScreenRenderTest.java'
foreach ($root in $VersionRoot) {
    $variant = if ($root -eq '1.20.1') { 'legacy' } elseif ($root -eq '1.21.1') { 'render' } else { 'extract' }
    $versionStubs = @(Get-ChildItem -LiteralPath (Join-Path $repository "tests/map-selection-screen-stubs/$variant") -Recurse -Filter '*.java' |
        ForEach-Object { $_.FullName })
    $source = if ($SourceOverride) { (Resolve-Path -LiteralPath $SourceOverride).Path } else {
        Join-Path $repository "$root/common/src/main/java/net/aosankaku/openmapdelta/gui/WaypointEditScreen.java"
    }
    $draft = Join-Path $repository "$root/common/src/main/java/net/aosankaku/openmapdelta/gui/WaypointEditDraft.java"
    $output = Join-Path $repository ".task-work/waypoint-screen-blur/$root"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $compileLog = Join-Path $output 'compile.log'
    & $javac '-J-Dfile.encoding=UTF-8' '--release' '17' '-encoding' 'UTF-8' '-d' $output `
        $commonStubs $editorStubs $versionStubs $draft $source $test *> $compileLog
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $compileLog -Tail 60
        throw "Waypoint editor render test compilation failed for $root."
    }
    & $java '-Dfile.encoding=UTF-8' "-Dwaypoint.edit.version=$root" '-cp' $output `
        net.aosankaku.openmapdelta.gui.WaypointEditScreenRenderTest
    if ($LASTEXITCODE -ne 0) { throw "Waypoint editor render test failed for $root." }
}
