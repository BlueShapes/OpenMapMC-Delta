# Compiles the real 26.2 MarkerListWidget against minimal external API stubs and runs its controller lifecycle checks.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }

$testOutput = Join-Path $repository '26.2/common/build/controller-widget-tests'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$guiSource = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/gui'
$sources = @(
    (Join-Path $guiSource 'MarkerListWidget.java'),
    (Join-Path $guiSource 'WaypointListModel.java'),
    (Join-Path $guiSource 'FullscreenControllerPolicy.java'),
    (Join-Path $guiSource 'ControllerNavigationLayout.java'),
    (Join-Path $repository 'tests/MarkerListControllerTest.java')
)
$sources += @(Get-ChildItem -Path (Join-Path $repository 'tests/controller-widget-stubs') -Recurse -Filter '*.java' | ForEach-Object FullName)

& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $testOutput @sources
if ($LASTEXITCODE -ne 0) { throw 'MarkerList controller widget test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $testOutput MarkerListControllerTest
if ($LASTEXITCODE -ne 0) { throw 'MarkerList controller widget tests failed.' }
