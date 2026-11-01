# Compiles the dependency-free editor state/geometry policy tests.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }

$testOutput = Join-Path $repository '26.2/common/build/area-editor-state-tests'
$areaSource = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/area'
$guiSource = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/gui'
$sources = @(
    (Join-Path $areaSource 'MapArea.java'),
    (Join-Path $areaSource 'AreaDocument.java'),
    (Join-Path $areaSource 'AreaGeometryOps.java'),
    (Join-Path $guiSource 'AreaEditorState.java'),
    (Join-Path $PSScriptRoot '../tests/AreaEditorStateTest.java')
)
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $testOutput @sources
if ($LASTEXITCODE -ne 0) { throw 'Area editor state test compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $testOutput net.aosankaku.openmapdelta.gui.AreaEditorStateTest
if ($LASTEXITCODE -ne 0) { throw 'Area editor state tests failed.' }
