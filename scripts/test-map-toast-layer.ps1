# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [string]$JdkHome = 'C:/Program Files/Java/jdk-17',
    [string]$GradleCache = "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1",
    [string]$FabricGameJar,
    [string]$NeoForgeGameJar
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$FabricGameJar) { $FabricGameJar = Join-Path $repository '26.2/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-043a8b3edf/26.2/minecraft-merged-043a8b3edf-26.2.jar' }
if (!$NeoForgeGameJar) { $NeoForgeGameJar = Join-Path $repository '26.2/neoforge/build/moddev/artifacts/minecraft-patched-26.2.0.88.jar' }
function CachedJar([string]$module) {
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $GradleCache $module) -Recurse -Filter '*.jar' -File)
    if ($jars.Count -ne 1) { throw "Expected one cached library JAR: $module" }
    $jars[0].FullName
}
$libraries = @((CachedJar 'net.fabricmc/sponge-mixin/0.17.3+mixin.0.8.7'),
    (CachedJar 'io.github.llamalad7/mixinextras-common/0.5.3'), (CachedJar 'org.ow2.asm/asm/9.10.1'))
$classpath = $libraries -join [IO.Path]::PathSeparator
$stubs = @(Get-ChildItem -LiteralPath (Join-Path $repository 'tests/map-toast-stubs') -Recurse -Filter '*.java' -File | ForEach-Object FullName)
foreach ($loader in @('fabric','neoforge')) {
    $gameJar = if ($loader -eq 'fabric') { $FabricGameJar } else { $NeoForgeGameJar }
    if (!(Test-Path -LiteralPath $gameJar -PathType Leaf)) { throw "Build 26.2 $loader first or pass its cached game JAR." }
    $output = Join-Path $repository ".task-work/map-toast-layer-tests/26.2/$loader"
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $source = Join-Path $repository "26.2/$loader/src/main/java/net/aosankaku/openmapdelta/mixin/MapScreenToastLayerMixin.java"
    $minimap = Join-Path $repository "26.2/$loader/src/main/java/net/aosankaku/openmapdelta/mixin/MixinGui.java"
    $config = Get-Content -LiteralPath (Join-Path $repository "26.2/$loader/src/main/resources/openmapdelta.$loader.mixins.json") -Raw | ConvertFrom-Json
    if ('MapScreenToastLayerMixin' -notin $config.client -or 'MixinGui' -notin $config.client) { throw "Map HUD/toast mixins not enabled for $loader" }
    & (Join-Path $JdkHome 'bin/javac.exe') --release 17 -proc:none -encoding UTF-8 -cp $classpath -d $output $stubs $source $minimap (Join-Path $repository 'tests/MapScreenToastLayerTest.java') (Join-Path $repository 'tests/MinimapToastLayerTest.java') *> (Join-Path $output 'compile.log')
    if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $output 'compile.log') -Tail 35; throw "Toast check compilation failed: $loader" }
    & (Join-Path $JdkHome 'bin/java.exe') -cp ($output + [IO.Path]::PathSeparator + $classpath) net.aosankaku.openmapdelta.mixin.MapScreenToastLayerTest $loader $gameJar
    if ($LASTEXITCODE -ne 0) { throw "Toast layer check failed: $loader" }
    & (Join-Path $JdkHome 'bin/java.exe') -cp ($output + [IO.Path]::PathSeparator + $classpath) net.aosankaku.openmapdelta.mixin.MinimapToastLayerTest $loader $gameJar
    if ($LASTEXITCODE -ne 0) { throw "Minimap/toast layer check failed: $loader" }
}
