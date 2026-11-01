param([string]$JdkHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$repository=Split-Path -Parent $PSScriptRoot
$output=Join-Path $repository '26.2/common/build/controller-hint-render-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources=@(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/controller-hints-stubs') -Recurse -Filter '*.java' | ForEach-Object FullName)
$sources+=Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/gui/ControllerNavigationLayout.java'
$sources+=Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/gui/ControllerHintRenderer.java'
$sources+=Join-Path $repository 'tests/ControllerHintRendererTest.java'
$log=Join-Path $output 'compile.log'
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output @sources *> $log
if($LASTEXITCODE) { Get-Content -LiteralPath $log -Tail 15; throw 'Controller hint renderer fixture failed to compile.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.gui.ControllerHintRendererTest
if($LASTEXITCODE) { throw 'Controller hint renderer tests failed.' }
