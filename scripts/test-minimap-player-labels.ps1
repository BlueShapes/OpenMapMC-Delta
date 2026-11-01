# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '1.21.1',
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

$outputDirectory = Join-Path $repository "$VersionRoot/common/build/minimap-player-label-tests"
$dependencies = Join-Path $outputDirectory 'dependencies'
New-Item -ItemType Directory -Force -Path $dependencies | Out-Null
$localGson = Join-Path $dependencies 'gson.jar'
$localSlf4j = Join-Path $dependencies 'slf4j-api.jar'
Copy-Item -LiteralPath $GsonJar -Destination $localGson -Force
Copy-Item -LiteralPath $Slf4jJar -Destination $localSlf4j -Force
$dependencyClassPath = $localGson + [IO.Path]::PathSeparator + $localSlf4j
$sourceRoot = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$javaSourceRoot = Join-Path $repository "$VersionRoot/common/src/main/java"
$sources = @(
    'config/OpenMapDeltaConfig.java'
    'config/MinimapInfoType.java'
    'config/MinimapInfoSlot.java'
    'config/ConfigValue.java'
    'platform/DataPaths.java'
    'platform/DataDirectoryMigration.java'
    'platform/Services.java'
    'platform/services/IPlatformHelper.java'
    'render/WaypointClusterOrder.java'
) | ForEach-Object { Join-Path $sourceRoot $_ }
$playerLabelSource = Join-Path $sourceRoot 'marker/MinimapPlayerLabel.java'
if (Test-Path -LiteralPath $playerLabelSource) { $sources += $playerLabelSource }
if ($VersionRoot -in @('1.20.1', '1.21.1', '26.2')) {
    $sources += @('DeathPointPolicy.java', 'DeathPointData.java', 'DeathPointMotion.java') |
        ForEach-Object { Join-Path $sourceRoot "death/$_" }
}
$tests = @(
    (Join-Path $repository 'tests/MinimapPlayerLabelTest.java')
    (Join-Path $repository 'tests/MinimapPlayerLabelConfigTest.java')
)
$compileLog = Join-Path $outputDirectory 'compile.log'
& $javac '-J-Dfile.encoding=UTF-8' '-J-Duser.language=en' --release 17 -encoding UTF-8 `
    -sourcepath $javaSourceRoot -cp $dependencyClassPath -d $outputDirectory $sources $tests *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 40
    throw 'Minimap player label test compilation failed.'
}
$serviceDirectory = Join-Path $outputDirectory 'META-INF/services'
New-Item -ItemType Directory -Force -Path $serviceDirectory | Out-Null
[IO.File]::WriteAllText(
    (Join-Path $serviceDirectory 'net.aosankaku.openmapdelta.platform.services.IPlatformHelper'),
    'net.aosankaku.openmapdelta.config.MinimapPlayerLabelConfigTest$TestPlatform')
$classPath = $outputDirectory + [IO.Path]::PathSeparator + $dependencyClassPath
$fixture = Join-Path $outputDirectory ('config-fixture-' + [guid]::NewGuid().ToString('N'))
$gameFixture = Join-Path $outputDirectory ('game-fixture-' + [guid]::NewGuid().ToString('N'))
$stdoutLog = Join-Path $outputDirectory 'stdout.log'
$stderrLog = Join-Path $outputDirectory 'stderr.log'
foreach ($testClass in @(
    'net.aosankaku.openmapdelta.marker.MinimapPlayerLabelTest'
    'net.aosankaku.openmapdelta.config.MinimapPlayerLabelConfigTest'
)) {
    $process = Start-Process -FilePath $java -ArgumentList @(
        ('-Dminimap.test.configDir="' + $fixture + '"'),
        ('-Dminimap.test.gameDir="' + $gameFixture + '"'),
        '-cp', ('"' + $classPath + '"'),
        $testClass
    ) -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
    if ($process.ExitCode -ne 0) {
        Get-Content -LiteralPath $stderrLog -Tail 40
        Get-Content -LiteralPath $stdoutLog -Tail 40
        throw "Minimap player label tests failed: $testClass"
    }
    Get-Content -LiteralPath $stdoutLog -Tail 5
}
