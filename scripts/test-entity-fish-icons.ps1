param([string]$ClientJar = (Join-Path $env:USERPROFILE '.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar'))
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$source = Get-Content -LiteralPath (Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/marker/MarkerIconCatalog.java') -Raw
$archive = [IO.Compression.ZipFile]::OpenRead($ClientJar)
try {
    foreach ($fish in @('cod', 'salmon', 'pufferfish', 'tropical_fish')) {
        $definition = [regex]::Match($source, ('mobSprite\("' + $fish + '", "minecraft:([^"]+)"\)'))
        if (!$definition.Success) { throw "No dedicated fallback sprite for $fish." }
        $asset = $archive.GetEntry('assets/minecraft/' + $definition.Groups[1].Value)
        if ($null -eq $asset) { throw "Fish fallback texture not in 26.2 client: $fish" }
        if ($asset.Length -le 24) { throw "Fish fallback texture is empty/truncated: $fish" }
    }
} finally { $archive.Dispose() }
'26.2 fish fallback resource checks passed (4 species).'
