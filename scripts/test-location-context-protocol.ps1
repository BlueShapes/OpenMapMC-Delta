# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string[]]$VersionRoot = @('1.20.1', '1.21.1', '26.1.2', '26.2'),
    [string]$JdkHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
)
$ErrorActionPreference = 'Stop'
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
foreach ($root in $VersionRoot) {
    $temporary = Join-Path $env:TEMP ("odyssey-location-context-test-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $temporary | Out-Null
    try {
        $sources = @(
            (Join-Path $root 'common/src/main/java/net/aosankaku/openmapdelta/network/LocationContextRequest.java'),
            (Join-Path $root 'common/src/main/java/net/aosankaku/openmapdelta/network/LocationContextResponse.java'),
            (Join-Path $root 'common/src/main/java/net/aosankaku/openmapdelta/server/LocationContextStructureQuery.java'),
            'tests/LocationContextProtocolAndQueryTest.java'
        )
        & $javac --release 17 -encoding UTF-8 -d $temporary $sources
        if ($LASTEXITCODE -ne 0) { throw "javac failed for $root" }
        & $java -cp $temporary LocationContextProtocolAndQueryTest
        if ($LASTEXITCODE -ne 0) { throw "test failed for $root" }
    } finally {
        $resolvedTemporary = [IO.Path]::GetFullPath($temporary)
        $resolvedTempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
        if (!$resolvedTemporary.StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetFileName($resolvedTemporary) -notmatch '^odyssey-location-context-test-[0-9a-f]{32}$') {
            throw 'Refusing to remove an unexpected test output path.'
        }
        Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
    }
}
