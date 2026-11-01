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

if (!$GsonJar) {
    $gsonCache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
    $GsonJar = Get-ChildItem -LiteralPath $gsonCache -Recurse -Filter 'gson-*.jar' |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } |
        Sort-Object { [version]$_.Directory.Parent.Name } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (!$GsonJar -or !(Test-Path -LiteralPath $GsonJar)) { throw 'Pass -GsonJar with a cached Gson jar.' }

$output = Join-Path $repository "$VersionRoot/common/build/shared-waypoint-event-tests"
$workspaceTmp = Join-Path $output 'workspace-tmp'
New-Item -ItemType Directory -Force -Path $output, $workspaceTmp | Out-Null
$source = Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta"
$stubRoot = Join-Path $repository 'tests/server-config-stubs'
$sources = @(
    'shared/SharedWaypointServer.java'
    'shared/SharedWaypointStore.java'
    'shared/SharedWaypointData.java'
    'shared/SharedWaypoint.java'
    'shared/SharedWaypointAccess.java'
    'shared/SharedWaypointRequest.java'
    'shared/SharedWaypointSnapshot.java'
    'shared/SharedWaypointFeedback.java'
    'marker/WaypointFileStore.java'
    'marker/MarkerIconId.java'
) | ForEach-Object { Join-Path $source $_ }
$sources += @(
    'net/aosankaku/openmapdelta/OpenMapDeltaCommon.java'
    'net/aosankaku/openmapdelta/world/ServerWorldIdentity.java'
    'net/minecraft/server/MinecraftServer.java'
    'net/minecraft/server/level/ServerPlayer.java'
    'net/minecraft/world/level/storage/LevelResource.java'
) | ForEach-Object { Join-Path $stubRoot $_ }
$sources += Join-Path $repository 'tests/shared-event-stubs/net/aosankaku/openmapdelta/server/ServerConfig.java'
$sources += Join-Path $repository 'tests/SharedWaypointCreatedEventTest.java'
$compileLog = Join-Path $output 'compile.log'
& $javac '--release' '17' '-encoding' 'UTF-8' '-cp' $GsonJar '-d' $output @sources *> $compileLog
if ($LASTEXITCODE -ne 0) {
    Get-Content -LiteralPath $compileLog -Tail 60
    throw "$VersionRoot shared waypoint event test compilation failed."
}

$stdoutLog = Join-Path $output 'run.stdout.log'
$stderrLog = Join-Path $output 'run.stderr.log'
$process = Start-Process -FilePath $java -ArgumentList @(
    ('"-Djava.io.tmpdir={0}"' -f $workspaceTmp), '-Dfile.encoding=UTF-8', '-cp', ('"{0};{1}"' -f $output, $GsonJar),
    'net.aosankaku.openmapdelta.shared.SharedWaypointCreatedEventTest'
) -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
if ($process.ExitCode -ne 0) {
    Get-Content -LiteralPath $stderrLog -Encoding UTF8 -Tail 40
    Get-Content -LiteralPath $stdoutLog -Encoding UTF8 -Tail 40
    throw "$VersionRoot shared waypoint event tests failed."
}
Get-Content -LiteralPath $stdoutLog -Encoding UTF8 -Tail 5
