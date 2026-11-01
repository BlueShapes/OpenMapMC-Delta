param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $repository '26.2/common/build/entity-icon-render-tests'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$stubRoot = Join-Path $repository 'tests/entity-marker-stubs'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $stubRoot 'common') -File -Recurse | ForEach-Object FullName)
$sources += @(Get-ChildItem -LiteralPath (Join-Path $stubRoot 'modern') -File -Recurse | ForEach-Object FullName)
$sources += Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/marker/MarkerIconRenderer.java'
$sources += Join-Path $repository 'tests/EntityIconAspectTest.java'
$log = Join-Path $outputDirectory 'compile.log'
& (Join-Path $JdkHome 'bin/javac.exe') '--release' '17' '-encoding' 'UTF-8' '-d' $outputDirectory @sources *> $log
if ($LASTEXITCODE) { Get-Content -LiteralPath $log -TotalCount 20; throw 'Entity icon render fixture failed to compile.' }
& (Join-Path $JdkHome 'bin/java.exe') '-cp' $outputDirectory 'net.aosankaku.openmapdelta/marker/EntityIconAspectTest'.Replace('/', '.')
if ($LASTEXITCODE) { throw 'Entity icon render tests failed.' }
