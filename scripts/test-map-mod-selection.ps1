# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.20.1',
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GsonJar
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw 'JdkHome must contain bin/javac.exe and bin/java.exe.'
}

$gradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
if (!$GsonJar) {
    $cache = Join-Path $gradleCache 'caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1'
    $GsonJar = Get-ChildItem -LiteralPath $cache -Recurse -Filter 'gson-2.10.1.jar' |
        Select-Object -First 1 -ExpandProperty FullName
}
if (!$GsonJar -or !(Test-Path -LiteralPath $GsonJar)) { throw 'Pass -GsonJar with a Gson 2.10.1+ jar.' }

$outputDirectory = Join-Path $repository "$VersionRoot/common/build/map-mod-selection-tests"
$dependencies = Join-Path $outputDirectory 'dependencies'
New-Item -ItemType Directory -Force -Path $dependencies | Out-Null
$localGson = Join-Path $dependencies 'gson.jar'
Copy-Item -LiteralPath $GsonJar -Destination $localGson -Force
$classpath = $outputDirectory + [IO.Path]::PathSeparator + $localGson
$sourceRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/compat/mapselection"
$sources = Get-ChildItem -LiteralPath $sourceRoot -Filter '*.java' | ForEach-Object { $_.FullName }
$stubRoot = Join-Path $repository 'tests/map-mod-selection-stubs'
$stubs = Get-ChildItem -LiteralPath $stubRoot -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
$test = Join-Path $repository 'tests/MapModSelectionTest.java'
$compileLog = Join-Path $outputDirectory 'compile.log'
& $javac '-J-Dfile.encoding=UTF-8' '-J-Duser.language=en' --release 17 -encoding UTF-8 `
    -cp $localGson -d $outputDirectory $sources $stubs $test *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 60
    throw 'Map selection test compilation failed.'
}

$stderrLog = Join-Path $outputDirectory 'stderr.log'
& $java '-Dfile.encoding=UTF-8' '-Duser.language=en' "-Dmap.selection.test.root=$(Join-Path $outputDirectory 'fixtures')" -cp $classpath `
    net.aosankaku.openmapdelta.compat.mapselection.MapModSelectionTest 2> $stderrLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $stderrLog -Tail 60
    throw 'Map selection tests failed.'
}
