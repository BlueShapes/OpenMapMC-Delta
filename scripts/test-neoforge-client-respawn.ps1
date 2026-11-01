# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GradleCommand,
    [string]$BeforeFixJar
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$versionRoot = Join-Path $repository '1.21.1'
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (JDK 21 required).' }
if (!(Test-Path -LiteralPath (Join-Path $JdkHome 'bin/java.exe'))) {
    throw 'JdkHome must contain bin/java.exe.'
}
if (!$GradleCommand) { $GradleCommand = Join-Path $versionRoot 'gradlew.bat' }
$initScript = Join-Path $repository 'tests/neoforge-client-respawn.gradle'
$log = Join-Path $versionRoot 'neoforge/build/client-respawn-probe.log'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $log) | Out-Null
$env:JAVA_HOME = $JdkHome
$arguments = @('-p', $versionRoot, '-I', $initScript, ':neoforge:clientRespawnProbe', '--offline', '--no-daemon', '--console=plain')
if ($BeforeFixJar) {
    if (!(Test-Path -LiteralPath $BeforeFixJar)) { throw "Pre-fix probe JAR does not exist: $BeforeFixJar" }
    $arguments += "-PclientRespawnProbeJar=$((Resolve-Path -LiteralPath $BeforeFixJar).Path)"
}
& $GradleCommand @arguments *> $log
if ($LASTEXITCODE -ne 0) {
    Select-String -LiteralPath $log -Pattern 'AssertionError|Exception|error:|BUILD FAILED|What went wrong' -Context 2,5 | Select-Object -Last 45
    throw "NeoForge client respawn probe failed; see $log"
}
Select-String -LiteralPath $log -Pattern 'NeoForgeClientRespawnTest passed|BUILD SUCCESSFUL'
