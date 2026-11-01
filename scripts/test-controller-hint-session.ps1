param([string]$JdkHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$repository=Split-Path -Parent $PSScriptRoot
$source=Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta'
$output=Join-Path $repository '26.2/common/build/controller-hint-session-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$log=Join-Path $output 'compile.log'
& (Join-Path $JdkHome 'bin/javac.exe') '-J-Duser.language=en' '-J-Dfile.encoding=UTF-8' --release 17 -encoding UTF-8 -d $output `
    (Join-Path $source 'config/ControllerHintMode.java') `
    (Join-Path $source 'gui/ControllerHintSession.java') `
    (Join-Path $repository 'tests/ControllerHintSessionTest.java') *> $log
if($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 18; throw 'Controller hint session compile failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.gui.ControllerHintSessionTest
if($LASTEXITCODE -ne 0) { throw 'Controller hint session tests failed.' }
