# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2', 'all')]
    [string]$VersionRoot = 'all'
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$expectedLocales = @('en_us','ja_jp','zh_cn','zh_tw','zh_hk','yue_hk','fr_fr','hi_in','ru_ru','pt_br','pt_pt','es_es','de_de','cs_cz','ms_my','ro_ro')
$versions = if ($VersionRoot -eq 'all') { @('1.20.1','1.21.1','26.1.2','26.2') } else { @($VersionRoot) }
$strictUtf8 = [Text.UTF8Encoding]::new($false, $true)
$placeholderPattern = '%(?:[0-9]+\$)?[sd]|%%'
$keyPattern = '"((?:(?:screen|gui)\.(?:odysseymap|openmapdelta)|key\.(?:categories\.odysseymap|category\.openmapdelta|odysseymap)|odysseymap\.(?:marker|notify|import|navigation|shared|death)|web\.odysseymap|openmapdelta\.area)[A-Za-z0-9_.]*)"'
$controlKeys = @(
    'gui.odysseymap.setting.minimap_player_icon', 'gui.odysseymap.setting.minimap_other_player_icon',
    'gui.odysseymap.setting.minimap_rotate_player_head',
    'gui.odysseymap.setting.fullscreen_player_icon', 'gui.odysseymap.setting.fullscreen_rotate_player_head',
    'gui.odysseymap.value.vanilla_pointer', 'gui.odysseymap.value.triangle', 'gui.odysseymap.value.player_head',
    'gui.odysseymap.show_grid', 'gui.odysseymap.hide_grid', 'gui.odysseymap.setting.zoom_buttons',
    'gui.odysseymap.cave_layer_range', 'gui.odysseymap.setting.cave_layer_step',
    'gui.odysseymap.setting.navigation_max_targets', 'gui.odysseymap.setting.navigation_arrival_distance',
    'gui.odysseymap.context.message', 'gui.odysseymap.context.navigate', 'gui.odysseymap.context.stop_navigation',
    'gui.odysseymap.navigation.distance', 'odysseymap.navigation.full', 'odysseymap.navigation.arrived',
    'odysseymap.marker.tracked_notice', 'gui.odysseymap.notify.follow_player', 'screen.odysseymap.message',
    'gui.odysseymap.context.stop_all_navigation', 'gui.odysseymap.message.hint', 'gui.odysseymap.message.send', 'gui.odysseymap.message.failed',
    'gui.odysseymap.setting.shared_waypoints', 'gui.odysseymap.context.create_shared', 'screen.odysseymap.shared_waypoint_create',
    'gui.odysseymap.shared_read_only', 'odysseymap.shared.created', 'odysseymap.shared.updated', 'odysseymap.shared.deleted',
    'odysseymap.shared.denied', 'odysseymap.shared.unavailable', 'odysseymap.shared.invalid', 'odysseymap.shared.full',
    'odysseymap.shared.rate_limited', 'odysseymap.shared.storage_error',
    'gui.odysseymap.setting.cluster_label_order', 'gui.odysseymap.value.distance_ascending',
    'gui.odysseymap.value.distance_descending', 'gui.odysseymap.value.name',
    'odysseymap.waypoint.cluster_remaining',
    'gui.odysseymap.notify.next', 'screen.odysseymap.notify.message',
    'gui.odysseymap.notify.template.gather', 'gui.odysseymap.notify.template.come_here',
    'gui.odysseymap.notify.template.base_site', 'gui.odysseymap.notify.template.cave',
    'gui.odysseymap.notify.template.building', 'gui.odysseymap.notify.template.trade',
    'gui.odysseymap.notify.template.block', 'gui.odysseymap.notify.template.biome',
    'gui.odysseymap.notify.template.structure', 'gui.odysseymap.notify.template.found'
)
$checked = 0
foreach ($version in $versions) {
    $base = Join-Path $repository "$version/common/src/main"
    $directory = Join-Path $base 'resources/assets/openmapdelta/lang'
    $files = @(Get-ChildItem -LiteralPath $directory -Filter '*.json')
    $actualLocales = @($files.BaseName | Sort-Object)
    if (Compare-Object ($expectedLocales | Sort-Object) $actualLocales) { throw "${version}: locale list mismatch" }
    $english = $strictUtf8.GetString([IO.File]::ReadAllBytes((Join-Path $directory 'en_us.json'))) | ConvertFrom-Json -AsHashtable
    $keys = @($english.Keys | Sort-Object)
    # KeyMapping.Category builds this key from its Identifier; it is not a Java string literal.
    if ($version -eq '26.2' -and !$english.Contains('key.category.openmapdelta.keys')) {
        throw "${version}: missing key mapping category translation key.category.openmapdelta.keys"
    }
    foreach ($distanceKey in @('gui.odysseymap.waypoint_hide_distance','gui.odysseymap.waypoint_label_fade_distance')) {
        if (!$english.Contains($distanceKey)) { throw "${version}: missing distance setting translation $distanceKey" }
    }
    foreach ($controlKey in $controlKeys) {
        if (!$english.Contains($controlKey)) { throw "${version}: missing control translation $controlKey" }
    }
    foreach ($file in $files) {
        $data = $strictUtf8.GetString([IO.File]::ReadAllBytes($file.FullName)) | ConvertFrom-Json -AsHashtable
        if (Compare-Object $keys (@($data.Keys | Sort-Object))) { throw "$($file.Name): key set mismatch" }
        $nativeValues = 0
        foreach ($key in $keys) {
            if ($data[$key] -isnot [string] -or [string]::IsNullOrWhiteSpace($data[$key])) { throw "$($file.Name): missing value for $key" }
            if ($data[$key].Contains([string][char]0xFFFD)) { throw "$($file.Name): invalid replacement character in $key" }
            $expected = @([regex]::Matches($english[$key], $placeholderPattern) | ForEach-Object Value)
            $actual = @([regex]::Matches($data[$key], $placeholderPattern) | ForEach-Object Value)
            if (($expected -join '|') -ne ($actual -join '|')) { throw "$($file.Name): placeholder mismatch for $key" }
            if ($data[$key] -ne $english[$key]) { $nativeValues++ }
        }
        if ($file.BaseName -ne 'en_us' -and $nativeValues -lt ($keys.Count / 2)) { throw "$($file.Name): mostly English fallback values" }
        $checked++
    }
    foreach ($source in Get-ChildItem -LiteralPath (Join-Path $base 'java') -Filter '*.java' -Recurse) {
        $text = [IO.File]::ReadAllText($source.FullName)
        foreach ($match in [regex]::Matches($text, $keyPattern)) {
            $key = $match.Groups[1].Value
            if ($key.EndsWith('.')) { continue } # Enum key prefix: checked explicitly below.
            if (!$english.Contains($key)) { throw "$($source.Name): missing referenced key $key" }
        }
    }
    foreach ($enumValue in @('top_left','top_right','bottom_left','bottom_right','circle','square','heading_up','north_up','vanilla_pointer','low','medium','high')) {
        if (!$english.Contains("gui.odysseymap.value.$enumValue")) { throw "${version}: missing enum translation $enumValue" }
    }
    if ($version -eq '26.2') {
        foreach ($hintMode in @('first_time','always','never')) {
            if (!$english.Contains("gui.odysseymap.value.$hintMode")) { throw "${version}: missing controller hint mode $hintMode" }
        }
    }
    $metadata = [IO.File]::ReadAllText((Join-Path $base 'resources/pack.mcmeta')) | ConvertFrom-Json -AsHashtable
    if ($metadata.language.yue_hk.name -ne '粵語' -or $metadata.language.yue_hk.bidirectional) {
        throw "${version}: Cantonese language metadata mismatch"
    }
    Write-Output "$version locale checks passed ($($files.Count) locales, $($keys.Count) keys each)."
}
Write-Output "Checked $checked UTF-8 JSON catalogs: keys, placeholders, translations, source references, and language metadata."
