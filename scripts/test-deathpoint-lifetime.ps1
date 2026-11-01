# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
# Smart Deathpoints is integrated in the 1.20.1, 1.21.1 and 26.2 source roots.
param([ValidateSet('1.20.1', '1.21.1', '26.2')][string]$VersionRoot = '1.21.1', [string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$relativeRoot = 'common/src/main/java/net/aosankaku/openmapdelta/death'
$sourceRoot = Join-Path $repository "$VersionRoot/$relativeRoot"
$names = @('CorpseCompat.java', 'ItemLifetimeCompat.java')
foreach ($mode in @('loader', 'vanilla')) {
    $output = Join-Path $repository "$VersionRoot/common/build/deathpoint-lifetime-tests/$mode"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $sources = @($names | ForEach-Object { Join-Path $sourceRoot $_ }) + (Join-Path $repository 'tests/DeathPointLifetimeTest.java')
    $sources += Get-ChildItem -Path (Join-Path $repository 'tests/item-lifetime-stubs/shared') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
    $sources += Get-ChildItem -Path (Join-Path $repository "tests/item-lifetime-stubs/$mode") -Filter '*.java' -Recurse | ForEach-Object { $_.FullName }
    & (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output $sources
    if ($LASTEXITCODE -ne 0) { throw 'Lifetime test compilation failed.' }
    & (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.death.DeathPointLifetimeTest $mode
    if ($LASTEXITCODE -ne 0) { throw 'Lifetime tests failed.' }
}
