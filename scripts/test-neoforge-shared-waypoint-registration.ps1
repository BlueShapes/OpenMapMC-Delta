# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [switch]$Native,
    [ValidateSet('1.21.1','26.1.2','26.2')][string]$VersionRoot = '1.21.1',
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GradleCommand
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot

foreach ($version in @('1.21.1', '26.1.2', '26.2')) {
    $networkPath = Join-Path $repository "$version/neoforge/src/main/java/net/aosankaku/openmapdelta/network/NeoForgeNetwork.java"
    $network = Get-Content -Raw $networkPath
    $bidirectionalCount = [regex]::Matches($network, 'playBidirectional\(SharedWaypointPayload\.TYPE').Count
    $directionalCount = [regex]::Matches($network, 'playTo(?:Client|Server)\(SharedWaypointPayload\.TYPE').Count
    if ($bidirectionalCount -ne 1 -or $directionalCount -ne 0) {
        throw "${version}: expected exactly one bidirectional SharedWaypoint payload registration; found bidirectional=$bidirectionalCount directional=$directionalCount."
    }
    if ([regex]::Matches($network, 'playBidirectional\(LocationContextPayload\.TYPE').Count -ne 1 -or
        [regex]::Matches($network, 'playTo(?:Client|Server)\(LocationContextPayload\.TYPE').Count -ne 0 -or
        $network -notmatch 'payload\.kind\(\)\s*!=\s*LocationContextPayload\.REQUEST') {
        throw "${version}: expected one direction-gated bidirectional location context payload."
    }

    if ($version -eq '1.21.1') {
        if ($network -notmatch 'sender\s+instanceof\s+ServerPlayer' -or
            $network -notmatch 'payload\.kind\(\)\s*!=\s*SharedWaypointPayload\.REQUEST' -or
            $network -notmatch 'payload\.kind\(\)\s*!=\s*SharedWaypointPayload\.SNAPSHOT') {
            throw "${version}: the shared payload's single handler must gate server and client directions by sender and payload kind."
        }
    } else {
        $clientNetworkPath = Join-Path $repository "$version/neoforge/src/main/java/net/aosankaku/openmapdelta/network/NeoForgeSharedWaypointClientNetwork.java"
        $clientNetwork = Get-Content -Raw $clientNetworkPath
        $clientHandlerCount = [regex]::Matches($clientNetwork, 'event\.register\(SharedWaypointPayload\.TYPE').Count
        if ($clientHandlerCount -ne 1 -or
            $clientNetwork -notmatch 'RegisterClientPayloadHandlersEvent' -or
            $clientNetwork -notmatch 'payload\.kind\(\)\s*!=\s*SharedWaypointPayload\.SNAPSHOT' -or
            $clientNetwork -notmatch 'payload\.kind\(\)\s*!=\s*SharedWaypointPayload\.FEEDBACK') {
            throw "${version}: the separate client handler event must register the single payload type and accept only server-to-client kinds."
        }
        if ([regex]::Matches($clientNetwork, 'event\.register\(LocationContextPayload\.TYPE').Count -ne 1 -or
            $clientNetwork -notmatch 'payload\.kind\(\)\s*==\s*LocationContextPayload\.RESPONSE') {
            throw "${version}: the context client handler must accept only responses."
        }
    }

    if ($version -eq '26.2') {
        foreach ($payload in @('DeathPointPayload', 'ServerConfigSnapshotPayload')) {
            if ([regex]::Matches($network, "playToClient\($payload\.TYPE").Count -ne 1 -or
                [regex]::Matches($clientNetwork, "event\.register\($payload\.TYPE").Count -ne 1) {
                throw "${version}: expected one optional client-bound registration and client handler for $payload."
            }
        }
        if ([regex]::Matches($network, 'playToServer\(ServerConfigRequestPayload\.TYPE').Count -ne 1 -or
            $clientNetwork -notmatch 'ServerConfigRequestPayload\.TYPE\.id\(\)') {
            throw "${version}: expected one capability-gated server configuration request channel."
        }
    }
}

Write-Output 'NeoForge shared/context registration checks passed for 1.21.1, 26.1.2, and 26.2.'

if ($Native) {
    if (!$JdkHome -or !$GradleCommand) { throw 'Native mode needs -JdkHome and -GradleCommand for the selected version.' }
    $env:JAVA_HOME = $JdkHome
    $output = Join-Path $repository "$VersionRoot/common/build/registration-probe-tests"
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    & (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output (Join-Path $repository 'tests/NeoForgeSharedWaypointRegistrationTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Native registration probe compilation failed.' }
    $log = Join-Path $output 'registration.log'
    & $GradleCommand -p (Join-Path $repository $VersionRoot) -I (Join-Path $repository 'tests/neoforge-shared-waypoint-registration.gradle') ':neoforge:sharedWaypointRegistrationProbe' --offline --no-daemon *> $log
    if ($LASTEXITCODE -ne 0) { throw "Native registration probe failed; see $log" }
    Select-String -LiteralPath $log -Pattern 'NeoForge native|BUILD SUCCESSFUL'
}
