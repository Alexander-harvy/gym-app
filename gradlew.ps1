# PowerShell entry point preserves Unicode workspace paths on Windows.
if ($env:JAVA_HOME) { $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe' } else { $javaExe = 'java' }
Push-Location -LiteralPath $PSScriptRoot
try {
    & $javaExe '-classpath' 'gradle\wrapper\gradle-wrapper.jar' 'org.gradle.wrapper.GradleWrapperMain' @args
    $wrapperExitCode = $LASTEXITCODE
} finally { Pop-Location }
exit $wrapperExitCode
