# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }

$relativeHelper = 'common/src/main/java/net/aosankaku/openmapdelta/gui/SettingSliderRange.java'
$referenceHash = (Get-FileHash -LiteralPath (Join-Path $repository "1.20.1/$relativeHelper")).Hash
foreach ($root in @('1.20.1', '1.21.1', '26.1.2', '26.2')) {
    $otherHash = (Get-FileHash -LiteralPath (Join-Path $repository "$root/$relativeHelper")).Hash
    if ($referenceHash -ne $otherHash) { throw "Slider range helper differs: $root" }
}

$helper = Join-Path $repository "$VersionRoot/$relativeHelper"
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
$stubSources = @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/setting-slider-stubs') -Filter '*.java' -Recurse |
    ForEach-Object { $_.FullName })
$rangeTest = Join-Path $repository 'tests/SettingSliderRangeTest.java'
$rangeOutput = Join-Path $repository "$VersionRoot/common/build/setting-slider-tests"
New-Item -ItemType Directory -Force -Path $rangeOutput | Out-Null
& $javac --release 17 -encoding UTF-8 -d $rangeOutput $helper $rangeTest
if ($LASTEXITCODE -ne 0) { throw 'Slider range test compilation failed.' }
& $java -cp $rangeOutput net.aosankaku.openmapdelta.gui.SettingSliderRangeTest
if ($LASTEXITCODE -ne 0) { throw 'Slider range tests failed.' }

$widgetTest = Join-Path $repository 'tests/NumericSettingSliderTest.java'
foreach ($root in @('1.20.1', '26.2')) {
    $widget = Join-Path $repository "$root/common/src/main/java/net/aosankaku/openmapdelta/gui/NumericSettingSlider.java"
    $widgetRange = Join-Path $repository "$root/$relativeHelper"
    $output = Join-Path $repository "$VersionRoot/common/build/setting-slider-tests/$root"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    & $javac --release 17 -encoding UTF-8 -d $output $stubSources $widgetRange $widget $widgetTest
    if ($LASTEXITCODE -ne 0) { throw "Numeric slider widget test compilation failed for $root." }
    & $java -cp $output net.aosankaku.openmapdelta.gui.NumericSettingSliderTest
    if ($LASTEXITCODE -ne 0) { throw "Numeric slider widget tests failed for $root." }
}
