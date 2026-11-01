# Copyright (c) 2026 AoSankaku. OpenMapMC Delta modifications; derived from Odyssey Map by NAIZO under Apache-2.0.
param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$ForgeVersion = '47.4.0'
)
$ErrorActionPreference = 'Stop'

$repository = Split-Path -Parent $PSScriptRoot
$versionRoot = Join-Path $repository '1.20.1'
$testDirectory = Join-Path $repository '.task-work/web-capture-state-test'
$outputDirectory = Join-Path $versionRoot 'common/build/web-capture-state-tests'
$classpathFile = Join-Path $testDirectory 'runtime-classpath.txt'
$gradleInit = Join-Path $testDirectory 'write-capture-classpath.init.gradle'
$gradleLog = Join-Path $testDirectory 'gradle-classpath.log'
$javaLog = Join-Path $testDirectory 'test-output.log'
$compileLog = Join-Path $testDirectory 'javac-output.log'
$compileArguments = Join-Path $testDirectory 'javac.args'
$runArguments = Join-Path $testDirectory 'java.args'
$nativeDirectory = Join-Path $testDirectory 'natives'
$sourceFile = Join-Path $repository 'tests/WebMapCaptureStateTest.java'
$captureClass = Join-Path $versionRoot 'forge/build/classes/java/main/net/aosankaku/openmapdelta/web/WebMapTextureCapture.class'

if (!$JdkHome) { throw 'Set JAVA_HOME to a JDK 17 installation.' }
if (!(Test-Path -LiteralPath $captureClass)) { throw "Compile 1.20.1 Forge first; missing $captureClass" }
if (!(Test-Path -LiteralPath $testDirectory)) { New-Item -ItemType Directory -Force -Path $testDirectory | Out-Null }
if (!(Test-Path -LiteralPath $nativeDirectory)) { New-Item -ItemType Directory -Force -Path $nativeDirectory | Out-Null }
if (!(Test-Path -LiteralPath $outputDirectory)) { New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null }

$initSource = @'
gradle.projectsEvaluated {
    def forge = gradle.rootProject.findProject(':forge')
    if (forge == null) return
    forge.tasks.register('writeWebMapCaptureStateTestClasspath') {
        doLast {
            def paths = forge.sourceSets.main.runtimeClasspath.files.collect { it.canonicalPath }.unique()
            def destination = new File(forge.rootProject.projectDir.parentFile, '.task-work/web-capture-state-test/runtime-classpath.txt')
            destination.parentFile.mkdirs()
            destination.setText(paths.join(File.pathSeparator), 'UTF-8')
            println("Capture test classpath entries: ${paths.size()}")
        }
    }
}
'@
[IO.File]::WriteAllText($gradleInit, $initSource, [Text.UTF8Encoding]::new($false))

$oldPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
Push-Location $versionRoot
try {
    & '.\gradlew.bat' --offline --no-daemon --console=plain "-Pforge_version=$ForgeVersion" -I $gradleInit ':forge:writeWebMapCaptureStateTestClasspath' *> $gradleLog
    $gradleExit = $LASTEXITCODE
} finally {
    Pop-Location
    $ErrorActionPreference = $oldPreference
}
if ($gradleExit -ne 0) {
    $tail = Get-Content -LiteralPath $gradleLog -Tail 35 -ErrorAction SilentlyContinue
    throw "Failed to resolve the cached Forge runtime classpath (exit $gradleExit):`n$($tail -join "`n")"
}

$classpath = [IO.File]::ReadAllText($classpathFile).Trim()
if (!$classpath) { throw 'Gradle produced an empty Forge runtime classpath.' }
$classpath = $classpath.Replace('\', '/')
$java = Join-Path $JdkHome 'bin/java.exe'
$javac = Join-Path $JdkHome 'bin/javac.exe'

$compileArgsText = @('--release', '17', '-encoding', 'UTF-8', '-classpath', $classpath,
    '-d', $outputDirectory.Replace('\', '/'), $sourceFile.Replace('\', '/')) | ForEach-Object { '"' + $_ + '"' }
[IO.File]::WriteAllText($compileArguments, ($compileArgsText -join "`n"), [Text.UTF8Encoding]::new($false))
$oldPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $javac "@$($compileArguments.Replace('\', '/'))" *> $compileLog
$compileExit = $LASTEXITCODE
$ErrorActionPreference = $oldPreference
if ($compileExit -ne 0) {
    $tail = Get-Content -LiteralPath $compileLog -Tail 50 -ErrorAction SilentlyContinue
    throw "WebMapCaptureStateTest compilation failed:`n$($tail -join "`n")"
}

$runLines = [System.Collections.Generic.List[string]]::new()
$runLines.Add('"-Dorg.lwjgl.system.SharedLibraryExtractPath=' + $nativeDirectory.Replace('\', '/') + '"')
$runLines.Add('"-cp"')
$runLines.Add('"' + $outputDirectory.Replace('\', '/') + ';' + $classpath + '"')
$runLines.Add('"net.aosankaku.openmapdelta.web.WebMapCaptureStateTest"')
$runLines.Add('"' + $nativeDirectory.Replace('\', '/') + '"')
[IO.File]::WriteAllText($runArguments, ($runLines -join "`n"), [Text.UTF8Encoding]::new($false))
$ErrorActionPreference = 'Continue'
Push-Location $testDirectory
try {
    & $java "@$($runArguments.Replace('\', '/'))" *> $javaLog
    $runExit = $LASTEXITCODE
} finally {
    Pop-Location
}
$ErrorActionPreference = $oldPreference
if ($runExit -ne 0) {
    $tail = Get-Content -LiteralPath $javaLog -Tail 60 -ErrorAction SilentlyContinue
    throw "WebMapCaptureStateTest failed (exit $runExit):`n$($tail -join "`n")"
}
Get-Content -LiteralPath $javaLog -Tail 8
