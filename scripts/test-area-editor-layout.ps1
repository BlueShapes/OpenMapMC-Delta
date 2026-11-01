# Compiles the pure area editor layout policy and its small-screen regression tests.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }

$testOutput = Join-Path $repository '26.2/common/build/area-editor-layout-tests'
$areaSource = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/area'
$operationsSource = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/gui/AreaEditorOperations.java'
$layoutSource = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/gui/AreaEditorLayout.java'
$testSource = Join-Path $PSScriptRoot '../tests/AreaEditorTest.java'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$sources = @(
    (Join-Path $areaSource 'MapArea.java'),
    (Join-Path $areaSource 'AreaDocument.java'),
    (Join-Path $areaSource 'AreaGeometryOps.java'),
    $layoutSource,
    $operationsSource,
    $testSource
)
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $testOutput @sources
if ($LASTEXITCODE -ne 0) { throw 'Area editor layout test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $testOutput net.aosankaku.openmapdelta.gui.AreaEditorTest
if ($LASTEXITCODE -ne 0) { throw 'Area editor layout tests failed.' }
