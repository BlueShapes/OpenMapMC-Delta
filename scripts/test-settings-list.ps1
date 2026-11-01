# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param([string]$JdkHome = $env:JAVA_HOME)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }

$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
$output = Join-Path $repository '1.20.1/common/build/settings-list-tests'
$helper = Join-Path $repository '1.20.1/common/src/main/java/net/aosankaku/openmapdelta/gui/SettingsList.java'
$test = Join-Path $repository 'tests/SettingsListTest.java'
$stubs = @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/settings-list-stubs') -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName })

New-Item -ItemType Directory -Force -Path $output | Out-Null
& $javac --release 17 -encoding UTF-8 -d $output $stubs $helper $test
if ($LASTEXITCODE -ne 0) { throw 'Settings list test compilation failed.' }
& $java -cp $output net.aosankaku.openmapdelta.gui.SettingsListTest
if ($LASTEXITCODE -ne 0) { throw 'Settings list tests failed.' }
