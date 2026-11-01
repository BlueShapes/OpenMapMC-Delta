param(
    [ValidateSet('1.20.1','1.21.1','26.1.2','26.2','all')][string]$VersionRoot='26.2',
    [string]$JdkHome=$env:JAVA_HOME
)
$ErrorActionPreference='Stop'
$repository=Split-Path -Parent $PSScriptRoot
$versions=if($VersionRoot -eq 'all'){@('1.20.1','1.21.1','26.1.2','26.2')}else{@($VersionRoot)}
$stubs=@(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/controller-hints-stubs') -Recurse -Filter '*.java' | ForEach-Object FullName)
foreach($version in $versions) {
    $root=Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta"
    $output=Join-Path $repository "$version/common/build/controller-hint-tests"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $log=Join-Path $output 'compile.log'
    & (Join-Path $JdkHome 'bin/javac.exe') '-J-Duser.language=en' '-J-Dfile.encoding=UTF-8' --release 17 -encoding UTF-8 -d $output @stubs `
        (Join-Path $root 'compat/ControllerSupport.java') (Join-Path $root 'gui/ControllerNavigationLayout.java') `
        (Join-Path $repository 'tests/ControllerSupportHintsTest.java') `
        (Join-Path $repository 'tests/ControllerSupportGlyphHintsTest.java') *> $log
    if($LASTEXITCODE -ne 0) { Get-Content -LiteralPath $log -Tail 14; throw "Controller hints compile failed: $version" }
    $testClass=if($version -eq '26.2'){'ControllerSupportGlyphHintsTest'}else{'ControllerSupportHintsTest'}
    & (Join-Path $JdkHome 'bin/java.exe') -cp $output "net.aosankaku.openmapdelta.compat.$testClass"
    if($LASTEXITCODE -ne 0) { throw "Controller hints failed: $version" }
}
