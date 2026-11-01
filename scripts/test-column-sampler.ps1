# Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [ValidateSet('1.20.1', '1.21.1', '26.1.2', '26.2', 'all')]
    [string]$VersionRoot = 'all',
    [string]$JdkHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkHome) { $JdkHome = 'C:\Program Files\Java\jdk-17' }
$javac = Join-Path $JdkHome 'bin/javac.exe'
$java = Join-Path $JdkHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac) -or !(Test-Path -LiteralPath $java)) {
    throw "JDK not found under '$JdkHome'. Pass -JdkHome with JDK 17."
}

$versions = if ($VersionRoot -eq 'all') { @('1.20.1', '1.21.1', '26.1.2', '26.2') } else { @($VersionRoot) }
$stubRoot = Join-Path $repository 'tests/column-sampler-stubs'
$stubSources = @(Get-ChildItem -LiteralPath $stubRoot -Filter '*.java' -Recurse | ForEach-Object FullName)
$failures = [System.Collections.Generic.List[string]]::new()
$outputRoot = Join-Path $repository '.task-work/column-sampler-tests'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

function Quote-ProcessArgument([string]$value) {
    if ($value -match '\s') { return '"' + $value.Replace('"', '\"') + '"' }
    return $value
}

foreach ($version in $versions) {
    $common = Join-Path $repository "$version/common/src/main/java/net/aosankaku/openmapdelta"
    $sourceFiles = @(
        (Join-Path $common 'world/ColumnSampler.java'),
        (Join-Path $common 'world/ChunkColumnScan.java'),
        (Join-Path $common 'world/ChunkMapSnapshot.java'),
        (Join-Path $common 'world/DimensionStyle.java'),
        (Join-Path $common 'color/BiomeGrassColor.java'),
        (Join-Path $repository 'tests/ColumnSamplerTest.java')
    )
    $missing = @($sourceFiles | Where-Object { !(Test-Path -LiteralPath $_) })
    if ($missing.Count) { throw "$version is missing required source: $($missing -join ', ')" }

    $out = Join-Path $outputRoot $version
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $compileLog = Join-Path $outputRoot "$version-compile.out.log"
    $compileErrorLog = Join-Path $outputRoot "$version-compile.err.log"
    $compileArgs = @('--release', '17', '-encoding', 'UTF-8', '-d', $out) + $stubSources + $sourceFiles
    $compileArgs = @($compileArgs | ForEach-Object { Quote-ProcessArgument ([string]$_) })
    $compile = Start-Process -FilePath $javac -ArgumentList $compileArgs -WorkingDirectory $repository `
        -RedirectStandardOutput $compileLog -RedirectStandardError $compileErrorLog -Wait -PassThru -NoNewWindow
    if ($compile.ExitCode -ne 0) {
        if (Test-Path -LiteralPath $compileLog) { Get-Content -LiteralPath $compileLog -Tail 10 }
        if (Test-Path -LiteralPath $compileErrorLog) { Get-Content -LiteralPath $compileErrorLog -Tail 40 }
        throw "$version focused harness compilation failed (exit $($compile.ExitCode))."
    }
    Write-Output "$($version): compiled real sampler sources with --release 17"

    $stdout = Join-Path $outputRoot "$version-test.out.log"
    $stderr = Join-Path $outputRoot "$version-test.err.log"
    $runArgs = @()
    if ($version -eq '26.1.2' -or $version -eq '26.2') { $runArgs += '-Dsampler.test.argb=true' }
    $runArgs += @('-cp', $out, 'net.aosankaku.openmapdelta.world.ColumnSamplerTest')
    $runArgs = $runArgs | ForEach-Object { Quote-ProcessArgument ([string]$_) }
    $run = Start-Process -FilePath $java -ArgumentList $runArgs `
        -WorkingDirectory $repository -RedirectStandardOutput $stdout -RedirectStandardError $stderr -Wait -PassThru -NoNewWindow
    if ($run.ExitCode -eq 0) {
        Get-Content -LiteralPath $stdout -Tail 2
        Write-Output "$($version): PASS"
    } else {
        Write-Output "$($version): RED (exit $($run.ExitCode))"
        if (Test-Path -LiteralPath $stdout) { Get-Content -LiteralPath $stdout -Tail 4 }
        if (Test-Path -LiteralPath $stderr) { Get-Content -LiteralPath $stderr -Tail 12 }
        $failures.Add($version)
    }
}

if ($failures.Count) { throw "Focused runtime regression is RED for: $($failures -join ', ')" }
Write-Output "Verified live and snapshot column sampling for $($versions.Count) version root(s)."
