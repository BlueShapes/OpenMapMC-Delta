# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$GsonJarPath
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
if (!$GsonJarPath) {
    $gsonRoot = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
    if (Test-Path -LiteralPath $gsonRoot) {
        $GsonJarPath = Get-ChildItem -LiteralPath $gsonRoot -Directory |
            Sort-Object { [version]$_.Name } -Descending |
            ForEach-Object { Get-ChildItem -LiteralPath $_.FullName -Filter 'gson-*.jar' -File -Recurse | Select-Object -First 1 } |
            Select-Object -First 1 -ExpandProperty FullName
    }
}
if (!$GsonJarPath -or !(Test-Path -LiteralPath $GsonJarPath)) { throw 'A cached Gson jar is required; pass -GsonJarPath.' }

$areaRoot = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/area'
$markerRoot = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/marker'
$outputDirectory = Join-Path $repository '26.2/common/build/area-tests'
$taskDirectory = Join-Path $repository '.task-work/areas-20261028'
New-Item -ItemType Directory -Force -Path $outputDirectory, $taskDirectory | Out-Null

$sources = @(
    (Join-Path $areaRoot 'MapArea.java'),
    (Join-Path $areaRoot 'AreaDocument.java'),
    (Join-Path $areaRoot 'AreaGeometryOps.java'),
    (Join-Path $areaRoot 'AreaEditHistory.java'),
    (Join-Path $areaRoot 'AreaStore.java'),
    (Join-Path $markerRoot 'WaypointFileStore.java'),
    (Join-Path $PSScriptRoot '../tests/AreaDomainTest.java')
)

$compileOutput = & (Join-Path $JdkHome 'bin/javac.exe') -XDrawDiagnostics --release 17 -encoding UTF-8 -cp $GsonJarPath -d $outputDirectory $sources 2>&1
$compileExit = $LASTEXITCODE
if ($compileExit -ne 0) {
    $log = Join-Path $taskDirectory 'area-domain-red.log'
    $expectedMissingSources = @('MapArea.java', 'AreaDocument.java', 'AreaGeometryOps.java', 'AreaEditHistory.java', 'AreaStore.java') |
        Where-Object { !(Test-Path -LiteralPath (Join-Path $areaRoot $_)) }
    if ($expectedMissingSources.Count -gt 0) {
        $fixtureSource = Join-Path $repository 'tests/AreaDomainTest.java'
        $fixtureOutput = & (Join-Path $JdkHome 'bin/javac.exe') -XDrawDiagnostics -Xmaxerrs 500 --release 17 -encoding UTF-8 -cp "$GsonJarPath;$outputDirectory" -d $outputDirectory $fixtureSource 2>&1
        $fixtureExit = $LASTEXITCODE
        $fixtureDiagnostics = $fixtureOutput | Where-Object { $_ -match '^AreaDomainTest\.java:\d+:\d+: compiler\.err\.' }
        $unexpectedDiagnostics = $fixtureDiagnostics | Where-Object { $_ -notmatch '^AreaDomainTest\.java:\d+:\d+: compiler\.err\.(cant\.resolve|doesnt\.exist):' }
        if ($fixtureExit -eq 0 -or $fixtureDiagnostics.Count -eq 0 -or $unexpectedDiagnostics.Count -gt 0) {
            $fixtureOutput
            throw 'RED did not come from the intentionally absent area-domain API.'
        }
        $fixtureOutput | Set-Content -LiteralPath $log -Encoding utf8
        Write-Output "RED: AreaDomainTest compiles far enough to reference absent area-domain APIs; compiler output saved to $log."
        Write-Output ('Missing source: ' + ($expectedMissingSources -join ', '))
        exit 1
    }
    $compileOutput | Set-Content -LiteralPath $log -Encoding utf8
    $compileOutput
    throw 'Area domain test compilation failed; see compiler output above.'
}

& (Join-Path $JdkHome 'bin/java.exe') -cp "$outputDirectory;$GsonJarPath" net.aosankaku.openmapdelta.area.AreaDomainTest
if ($LASTEXITCODE -ne 0) { throw 'Area domain tests failed.' }
