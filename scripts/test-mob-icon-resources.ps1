# Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2')]
    [string]$VersionRoot = '26.2',
    [string]$ClientJar
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$ClientJar) {
    $gradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
    $ClientJar = Join-Path $gradleCache "caches/neoformruntime/artifacts/minecraft_${VersionRoot}_client.jar"
}
if (!(Test-Path -LiteralPath $ClientJar -PathType Leaf)) { throw 'Pass -ClientJar with the matching vanilla Minecraft client JAR.' }
$catalog = Get-Content -LiteralPath (Join-Path $repository "$VersionRoot/common/src/main/java/net/aosankaku/openmapdelta/marker/MarkerIconCatalog.java") -Raw
$mobs = [regex]::Matches($catalog, 'mob\("([^"]+)", "([^"]+)", (\d+), (\d+), (\d+), (\d+), (\d+), (\d+)\);')
if ($mobs.Count -eq 0) { throw 'No mob texture definitions found.' }
$failures = [Collections.Generic.List[string]]::new()
$archive = [IO.Compression.ZipFile]::OpenRead($ClientJar)
try {
    foreach ($mob in $mobs) {
        $name = $mob.Groups[1].Value
        $path = 'assets/minecraft/textures/entity/' + $mob.Groups[2].Value + '.png'
        $entry = $archive.GetEntry($path)
        if ($null -eq $entry) { $failures.Add("${name}: missing $path"); continue }
        $stream = $entry.Open()
        try {
            $header = [byte[]]::new(24)
            $offset = 0
            while ($offset -lt $header.Length) {
                $read = $stream.Read($header, $offset, $header.Length - $offset)
                if ($read -eq 0) { throw "Truncated PNG: $path" }
                $offset += $read
            }
        } finally { $stream.Dispose() }
        if ([Convert]::ToHexString($header[0..7]) -ne '89504E470D0A1A0A') { throw "Not a PNG: $path" }
        $width = [long]$header[16] * 16777216 + [long]$header[17] * 65536 + [long]$header[18] * 256 + $header[19]
        $height = [long]$header[20] * 16777216 + [long]$header[21] * 65536 + [long]$header[22] * 256 + $header[23]
        $u, $v, $cropWidth, $cropHeight, $textureWidth, $textureHeight = 3..8 | ForEach-Object { [int]$mob.Groups[$_].Value }
        if ($width -ne $textureWidth -or $height -ne $textureHeight) {
            $failures.Add("${name}: declared ${textureWidth}x${textureHeight}, PNG ${width}x${height}")
        }
        if ($cropWidth -le 0 -or $cropHeight -le 0 -or $u + $cropWidth -gt $width -or $v + $cropHeight -gt $height) {
            $failures.Add("${name}: crop lies outside PNG")
        }
    }
} finally { $archive.Dispose() }
if ($failures.Count -gt 0) {
    $failures | Write-Output
    throw "$VersionRoot mob texture checks failed ($($failures.Count) issues)."
}
Write-Output "$VersionRoot mob texture checks passed ($($mobs.Count) paths, PNG dimensions and crops)."
