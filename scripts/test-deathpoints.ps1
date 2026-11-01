# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
# Smart Deathpoints is integrated in the 1.20.1, 1.21.1 and 26.2 source roots.
param([ValidateSet('1.20.1', '1.21.1', '26.2')][string]$VersionRoot = '1.21.1', [string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$relativeRoot = 'common/src/main/java/net/aosankaku/openmapdelta/death'
$sourceRoot = Join-Path $repository "$VersionRoot/$relativeRoot"
$outputDirectory = Join-Path $repository "$VersionRoot/common/build/deathpoint-tests"
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $outputDirectory (Join-Path $sourceRoot 'DeathPointData.java') (Join-Path $sourceRoot 'DeathPointPolicy.java') (Join-Path $sourceRoot 'DeathPointMotion.java') (Join-Path $repository 'tests/DeathPointCoreTest.java') (Join-Path $repository 'tests/DeathPointMotionTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Deathpoint core test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.death.DeathPointCoreTest
if ($LASTEXITCODE -ne 0) { throw 'Deathpoint core tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $outputDirectory net.aosankaku.openmapdelta.death.DeathPointMotionTest
if ($LASTEXITCODE -ne 0) { throw 'Deathpoint motion tests failed.' }
