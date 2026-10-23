param(
    [string]$Maven = 'mvn',
    [string]$Repository = "$env:USERPROFILE/.m2/repository",
    [switch]$IncludeDatabase,
    [switch]$IncludeJavaFx,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if ($Maven -eq 'mvn' -and !(Get-Command mvn -ErrorAction SilentlyContinue)) {
    $Maven = 'C:/Program Files/Apache NetBeans/java/maven/bin/mvn.cmd'
}
$mavenArguments = @("-Dmaven.repo.local=$Repository")
if ($Offline) { $mavenArguments += '-o' }
& $Maven @mavenArguments test
if ($LASTEXITCODE -ne 0) { throw 'Maven tests failed.' }
$classpath = "target/test-classes;target/classes;$Repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar"
& java -cp $classpath lk.ac.ruhuna.ftms.service.CourseServiceTest
if ($LASTEXITCODE -ne 0) { throw 'Course Management service regression failed.' }
if ($IncludeDatabase) {
    & java -cp $classpath lk.ac.ruhuna.ftms.dao.CourseOfferingDatabaseSmokeTest --safe-write
    if ($LASTEXITCODE -ne 0) { throw 'Course Offering MySQL checks failed.' }
}
if ($IncludeJavaFx) {
    $fx = @('base', 'graphics', 'controls', 'fxml') | ForEach-Object {
        "$Repository/org/openjfx/javafx-$_/25/javafx-$_-25-win.jar"
    }
    foreach ($runner in @('CourseOfferingViewSmokeTest', 'CourseViewSmokeTest')) {
        $ErrorActionPreference = 'Continue'
        & java "-Djavafx.cachedir=$projectRoot/target/javafx-cache" --module-path ($fx -join ';') `
            --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics `
            -cp $classpath "lk.ac.ruhuna.ftms.controller.$runner" --live-read
        $runnerExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($runnerExit -ne 0) { throw "$runner failed." }
    }
}
