param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (!$JdkHome) { throw 'Pass -JdkHome with Java 17 or newer.' }
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path $repository '26.2/common/build/emojiful-map-text-tests'
$fixtures = Join-Path $output 'optional'
New-Item -ItemType Directory -Force -Path $output, $fixtures | Out-Null
$sourceRoot = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/emojiful-text-stubs') -Recurse -Filter '*.java' -File | ForEach-Object FullName)
$sources += Join-Path $sourceRoot 'marker/MarkerLabelRenderer.java'
$sources += Join-Path $sourceRoot 'marker/MarkerHoverStyle.java'
foreach ($relative in @('compat/EmojifulTextCompat.java', 'util/MapTextFitting.java')) {
    $candidate = Join-Path $sourceRoot $relative
    if (Test-Path -LiteralPath $candidate) { $sources += $candidate }
}
$sources += Join-Path $repository 'tests/EmojifulTextAbsentTest.java'
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output @sources *> (Join-Path $output 'compile.log')
if ($LASTEXITCODE) { Get-Content -LiteralPath (Join-Path $output 'compile.log') -Tail 20; throw 'Map text fixture compilation failed.' }
$optionalSources = @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/fixtures/emojiful-text') -Recurse -Filter '*.java' -File | ForEach-Object FullName)
$optionalSources += Join-Path $repository 'tests/EmojifulMapTextTest.java'
& (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -cp $output -d $fixtures @optionalSources *> (Join-Path $output 'optional-compile.log')
if ($LASTEXITCODE) { Get-Content -LiteralPath (Join-Path $output 'optional-compile.log') -Tail 20; throw 'Optional text fixture compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp "$output;$fixtures" net.aosankaku.openmapdelta.marker.EmojifulMapTextTest
if ($LASTEXITCODE) { throw 'Emojiful map text test failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp $output net.aosankaku.openmapdelta.compat.EmojifulTextAbsentTest
if ($LASTEXITCODE) { throw 'Absent-classpath text test failed.' }
