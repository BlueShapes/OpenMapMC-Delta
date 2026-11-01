# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw 'JdkHome must contain bin/javac.exe and bin/java.exe.'
}

$stubRoot = Join-Path $repository 'tests/minimap-info-renderer-stubs'
$stubs = @(Get-ChildItem -LiteralPath $stubRoot -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$sourceRoot = Join-Path $repository '1.21.1/common/src/main/java/net/aosankaku/openmapdelta/render'
$sources = @(
    (Join-Path $sourceRoot 'MinimapSizing.java')
    (Join-Path $sourceRoot 'MinimapInfoLayout.java')
    (Join-Path $sourceRoot 'MinimapInfoRenderer.java')
    (Join-Path $repository 'tests/MinimapInfoRendererCaptureTest.java')
)
$output = Join-Path $repository '1.21.1/common/build/minimap-info-renderer-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$compileLog = Join-Path $output 'compile.log'
& $javac '-J-Dfile.encoding=UTF-8' '--release' '17' '-encoding' 'UTF-8' '-d' $output $stubs $sources *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 50
    throw 'Minimap info renderer capture test compilation failed.'
}
& $java '-Dfile.encoding=UTF-8' '-cp' $output 'net.aosankaku.openmapdelta.render.MinimapInfoRendererCaptureTest'
if ($LASTEXITCODE -ne 0) { throw 'Minimap info renderer capture test failed.' }
