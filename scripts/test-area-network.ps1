# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome (Java 17 or newer).' }
$areaRoot = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/area'
$networkRoot = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/network'
$output = Join-Path $repository '26.2/common/build/area-network-tests'
$task = Join-Path $repository '.task-work/areas-20261028'
New-Item -ItemType Directory -Force -Path $output, $task | Out-Null
$sources = @(
    (Join-Path $areaRoot 'AreaGeometryOps.java'),
    (Join-Path $areaRoot 'MapArea.java'),
    (Join-Path $areaRoot 'AreaDocument.java'),
    (Join-Path $areaRoot 'AreaWire.java'),
    (Join-Path $areaRoot 'AreaSnapshotAssembler.java'),
    (Join-Path $areaRoot 'AreaTransactions.java'),
    (Join-Path $areaRoot 'AreaClient.java'),
    (Join-Path $PSScriptRoot '../tests/AreaNetworkTest.java')
)
$compileOutput = & (Join-Path $JdkHome 'bin/javac.exe') --release 17 -encoding UTF-8 -d $output $sources 2>&1
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    $compileOutput | Set-Content -LiteralPath (Join-Path $task 'area-network-red.log') -Encoding utf8
    $missingSources = @('AreaGeometryOps.java', 'MapArea.java', 'AreaDocument.java', 'AreaWire.java', 'AreaSnapshotAssembler.java', 'AreaTransactions.java', 'AreaClient.java') |
        Where-Object { !(Test-Path -LiteralPath (Join-Path $areaRoot $_)) }
    if ($missingSources.Count -gt 0) {
        Write-Output "RED: area-network dependencies are not implemented yet; compiler output saved under $task."
        Write-Output ('Missing source: ' + ($missingSources -join ', '))
        exit 1
    }
    $compileOutput
    throw 'Area network test compilation failed.'
}
& (Join-Path $JdkHome 'bin/java.exe') '-Djava.awt.headless=true' -cp $output net.aosankaku.openmapdelta.area.AreaNetworkTest
if ($LASTEXITCODE -ne 0) { throw 'Area network tests failed.' }
