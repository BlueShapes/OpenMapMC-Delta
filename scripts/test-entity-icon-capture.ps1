param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $repository '26.2/common/build/entity-icon-capture-tests'
$stubRoot = Join-Path $repository 'tests/entity-icon-capture-stubs'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$sources = @(Get-ChildItem -LiteralPath $stubRoot -File -Recurse | ForEach-Object FullName)
$sources += @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests') -Filter 'Entity*CaptureTest.java' | ForEach-Object FullName)
$sources += Join-Path $repository 'tests/EntityIconFramingTest.java'
$sources += Join-Path $repository 'tests/EntityIconPixelsTest.java'
$sources += Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/marker/EntityIconPixels.java'
$sources += Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/marker/EntityHeadCapture.java'
$sources += Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/marker/EntityIconFraming.java'
$log = Join-Path $outputDirectory 'compile.log'
& (Join-Path $JdkHome 'bin/javac.exe') '--release' '17' '-encoding' 'UTF-8' '-d' $outputDirectory @sources *> $log
if ($LASTEXITCODE) { Get-Content -LiteralPath $log -TotalCount 24; throw 'Entity icon capture fixtures failed to compile.' }
foreach ($testClass in @('EntityIconPixelsTest', 'EntityHeadCaptureTest', 'EntityIconFramingTest')) {
    & (Join-Path $JdkHome 'bin/java.exe') '-cp' $outputDirectory "net.aosankaku.openmapdelta.marker.$testClass"
    if ($LASTEXITCODE) { throw "Entity icon capture test failed: $testClass" }
}
