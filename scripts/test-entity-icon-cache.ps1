# Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
param([string]$JdkHome = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw 'JdkHome must contain bin/javac.exe and bin/java.exe.'
}

$markerRoot = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/marker'
$outputDirectory = Join-Path $repository '26.2/common/build/entity-icon-cache-tests'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @(
    (Join-Path $markerRoot 'EntityIconCache.java')
    (Join-Path $repository 'tests/EntityIconCacheTest.java')
)
$compileOutput = & $javac '-J-Dfile.encoding=UTF-8' --release 17 -encoding UTF-8 -d $outputDirectory @sources 2>&1
if ($LASTEXITCODE -ne 0) {
    $compileOutput | Select-Object -First 30 | Write-Output
    throw 'Entity icon cache tests failed to compile.'
}

& $java -cp $outputDirectory net.aosankaku.openmapdelta.marker.EntityIconCacheTest
if ($LASTEXITCODE -ne 0) { throw 'Entity icon cache tests failed.' }
