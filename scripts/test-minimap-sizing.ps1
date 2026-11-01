# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GsonJar,
    [string]$Slf4jJar
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
if (!$Slf4jJar) {
    $cache = Join-Path $gradleCache 'caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.9'
    $Slf4jJar = Get-ChildItem -LiteralPath $cache -Recurse -Filter 'slf4j-api-2.0.9.jar' |
        Select-Object -First 1 -ExpandProperty FullName
}
if (!$GsonJar -or !(Test-Path -LiteralPath $GsonJar)) { throw 'Pass -GsonJar with a Gson 2.10.1+ jar.' }
if (!$Slf4jJar -or !(Test-Path -LiteralPath $Slf4jJar)) { throw 'Pass -Slf4jJar with an SLF4J API 2.x jar.' }

$outputDirectory = Join-Path $repository "$VersionRoot/common/build/minimap-sizing-tests"
$dependencies = Join-Path $outputDirectory 'dependencies'
New-Item -ItemType Directory -Force -Path $dependencies | Out-Null
# Keep test dependencies and generated classes under this version's build directory.
$localGson = Join-Path $dependencies 'gson.jar'
$localSlf4j = Join-Path $dependencies 'slf4j-api.jar'
Copy-Item -LiteralPath $GsonJar -Destination $localGson -Force
Copy-Item -LiteralPath $Slf4jJar -Destination $localSlf4j -Force
$dependencyClassPath = $localGson + [IO.Path]::PathSeparator + $localSlf4j
$sourceRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$sources = @(
    'config/OpenMapDeltaConfig.java'
    'config/MinimapLayerMode.java'
    'config/MinimapInfoType.java'
    'config/MinimapInfoSlot.java'
    'config/ConfigValue.java'
    'platform/DataPaths.java'
    'platform/DataDirectoryMigration.java'
    'platform/Services.java'
    'platform/services/IPlatformHelper.java'
    'client/RuntimeClientState.java'
    'render/MinimapSizing.java'
    'render/MinimapShape.java'
    'render/MinimapPlayerIconMath.java'
    'render/MinimapInfoLayout.java'
    'render/WaypointClusterOrder.java'
) | ForEach-Object { Join-Path $sourceRoot $_ }
if ($VersionRoot -in @('1.20.1', '1.21.1', '26.2')) {
    $sources += @('DeathPointPolicy.java', 'DeathPointData.java', 'DeathPointMotion.java') |
        ForEach-Object { Join-Path $sourceRoot "death/$_" }
}
$tests = Join-Path $repository 'tests/MinimapSizingTest.java'
if ($VersionRoot -in @('1.20.1', '1.21.1', '26.2')) { $tests = @($tests, (Join-Path $repository 'tests/DeathPointConfigTest.java')) }
if ($VersionRoot -eq '1.21.1') { $tests += (Join-Path $repository 'tests/MinimapInfoAlignmentConfigTest.java') }
$compileLog = Join-Path $outputDirectory 'compile.log'
& $javac '-J-Dfile.encoding=UTF-8' '-J-Duser.language=en' --release 17 -encoding UTF-8 -cp $dependencyClassPath -d $outputDirectory $sources $tests *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 40
    throw 'Minimap sizing test compilation failed.'
}
$serviceDirectory = Join-Path $outputDirectory 'META-INF/services'
New-Item -ItemType Directory -Force -Path $serviceDirectory | Out-Null
[IO.File]::WriteAllText(
    (Join-Path $serviceDirectory 'net.aosankaku.openmapdelta.platform.services.IPlatformHelper'),
    'net.aosankaku.openmapdelta.render.MinimapSizingTest$TestPlatform')
$classPath = $outputDirectory + [IO.Path]::PathSeparator + $dependencyClassPath
$stderrLog = Join-Path $outputDirectory 'stderr.log'
function Invoke-ConfigTest([string]$testClass, [string]$fixtureName, [string]$failureMessage) {
    $fixture = Join-Path $outputDirectory ($fixtureName + [guid]::NewGuid().ToString('N'))
    $stdoutLog = Join-Path $outputDirectory ($fixtureName + 'stdout.log')
    $process = Start-Process -FilePath $java -ArgumentList @(
        ('-Dminimap.test.configDir="' + $fixture + '"'), '-cp', ('"' + $classPath + '"'), $testClass
    ) -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
    if ($process.ExitCode -ne 0) {
        Get-Content -LiteralPath $stderrLog -Tail 40
        Get-Content -LiteralPath $stdoutLog -Tail 40
        throw $failureMessage
    }
    Get-Content -LiteralPath $stdoutLog -Tail 5
}
# Isolate each run from legacy config files left by earlier migration checks.
Invoke-ConfigTest 'net.aosankaku.openmapdelta.render.MinimapSizingTest' 'config-fixture-' 'Minimap sizing tests failed.'
if ($VersionRoot -in @('1.20.1', '1.21.1', '26.2')) {
    Invoke-ConfigTest 'net.aosankaku.openmapdelta.render.DeathPointConfigTest' 'death-config-fixture-' 'Deathpoint settings tests failed.'
}
if ($VersionRoot -eq '1.21.1') {
    Invoke-ConfigTest 'net.aosankaku.openmapdelta.render.MinimapInfoAlignmentConfigTest' 'info-alignment-config-fixture-' 'Minimap corner alignment config tests failed.'
}
