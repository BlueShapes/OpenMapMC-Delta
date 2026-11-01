# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.21.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$source = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/util/RenderUtil.java"
$stubRoot = Join-Path $repository 'tests/player-pointer-render-stubs'
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/player-pointer-render-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$compileLog = Join-Path $outputDirectory 'compile.log'
$stubs = @(Get-ChildItem -LiteralPath $stubRoot -Recurse -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    $stubs $source (Join-Path $repository 'tests/VanillaPlayerPointerRenderTest.java') *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 30
    throw 'Vanilla player pointer render test compilation failed.'
}
& (Join-Path $JdkHome 'bin/java.exe') "-Dpointer.version=$VersionRoot" -cp $outputDirectory `
    net.aosankaku.openmapdelta.util.VanillaPlayerPointerRenderTest
if ($LASTEXITCODE -ne 0) { throw 'Vanilla player pointer render tests failed.' }
