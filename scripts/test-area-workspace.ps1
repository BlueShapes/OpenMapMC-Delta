param([string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome.' }
$gsonRoot = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
$gson = Get-ChildItem -LiteralPath $gsonRoot -Filter 'gson-*.jar' -File -Recurse |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } | Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (!$gson) { throw 'Cached Gson is required.' }
$area = Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/area'
$output = Join-Path $repository '26.2/common/build/area-workspace-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @('MapArea.java', 'AreaDocument.java', 'AreaGeometryOps.java', 'AreaStore.java', 'AreaWorkspace.java', 'AreaMinimapProjection.java') |
    ForEach-Object { Join-Path $area $_ }
$sources += Join-Path $repository '26.2/common/src/main/java/net/aosankaku/openmapdelta/marker/WaypointFileStore.java'
$sources += Join-Path $repository 'tests/AreaWorkspaceTest.java'
$sources += Join-Path $repository 'tests/AreaMinimapProjectionTest.java'
& (Join-Path $JdkHome 'bin/javac.exe') '-J-Dfile.encoding=UTF-8' --release 17 -encoding UTF-8 -cp $gson -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Area workspace compilation failed.' }
& (Join-Path $JdkHome 'bin/java.exe') '-Djava.awt.headless=true' -cp "$output;$gson" net.aosankaku.openmapdelta.area.AreaWorkspaceTest $output
if ($LASTEXITCODE -ne 0) { throw 'Area workspace tests failed.' }
& (Join-Path $JdkHome 'bin/java.exe') -cp "$output;$gson" net.aosankaku.openmapdelta.area.AreaMinimapProjectionTest
if ($LASTEXITCODE -ne 0) { throw 'Area minimap projection tests failed.' }
