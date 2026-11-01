# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1')]
    [string]$VersionRoot = '1.21.1',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javaRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$stubRoot = Join-Path $repository 'tests/marker-sprite-stubs'
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/marker-icon-sprite-tests"
$compileLog = Join-Path $outputDirectory 'compile.log'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$stubSources = @(Get-ChildItem -LiteralPath $stubRoot -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
$spriteCache = Join-Path $javaRoot 'render/MarkerIconSpriteCache.java'
$imagePixels = Join-Path $javaRoot 'web/WebMapImagePixels.java'
$test = Join-Path $repository 'tests/MarkerIconSpriteCacheTest.java'
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory `
    $stubSources $spriteCache $imagePixels $test *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 30
    throw "Marker icon sprite test compilation failed for $VersionRoot."
}
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.render.MarkerIconSpriteCacheTest
if ($LASTEXITCODE -ne 0) { throw "Marker icon sprite test failed for $VersionRoot." }
