# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
# Smart Deathpoints is integrated in the 1.20.1, 1.21.1 and 26.2 source roots.
param([ValidateSet('1.20.1', '1.21.1', '26.2')][string]$VersionRoot = '1.21.1', [string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$gsonRoot = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
$gsonJar = Get-ChildItem -Path $gsonRoot -Filter 'gson-*.jar' -Recurse |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object FullName -Descending | Select-Object -First 1
if (!$gsonJar) { throw "Gson was not found under $gsonRoot." }
$relativeRoot = 'common/src/main/java/net/aosankaku/openmapdelta/death'
$sourceRoot = Join-Path $repository "$VersionRoot/$relativeRoot"
$names = @('DeathPointUpdate.java','DeathPointInbox.java','DeathDropLedger.java','DeathLootTimer.java')
$output = Join-Path $repository "$VersionRoot/common/build/deathpoint-protocol-tests"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$localGson = Join-Path $output $gsonJar.Name
Copy-Item -LiteralPath $gsonJar.FullName -Destination $localGson -Force
$sources = @($names | ForEach-Object { Join-Path $sourceRoot $_ }) + (Join-Path $repository 'tests/DeathPointProtocolTest.java')
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $localGson -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Deathpoint protocol test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp "$output;$localGson" DeathPointProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'Deathpoint protocol tests failed.' }
