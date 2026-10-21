param(
    [string]$Maven = 'mvn',
    [string]$Repository = "$env:USERPROFILE/.m2/repository",
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if ($Maven -eq 'mvn' -and !(Get-Command mvn -ErrorAction SilentlyContinue)) {
    $Maven = 'C:/Program Files/Apache NetBeans/java/maven/bin/mvn.cmd'
}
$logRoot = Join-Path $projectRoot 'target/student-test-logs'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null
$mavenArguments = @("-Dmaven.repo.local=$Repository")
if ($Offline) { $mavenArguments += '-o' }
$ErrorActionPreference = 'Continue'
& $Maven @mavenArguments '-Dtest=StudentAccessTest,StudentPortalMySqlIT,LegacyRegressionTest,AcademicCalculationsTest,AcademicFxmlTest,AcademicMySqlIT' test *> (Join-Path $logRoot 'maven.log')
$code = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
Get-Content -LiteralPath (Join-Path $logRoot 'maven.log') -Tail 18
if ($code -ne 0) { throw 'Student Maven tests failed.' }
$fx = @('base','graphics','controls','fxml') | ForEach-Object {
    "$Repository/org/openjfx/javafx-$_/25/javafx-$_-25-win.jar"
}
$classpath = "target/test-classes;target/classes;$Repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar"
$summary = @()
foreach ($runner in @('controller.StudentPortalViewSmokeTest','controller.AcademicViewsSmokeTest',
        'controller.ProfileViewSmokeTest','controller.TechnicalOfficerDashboardSmokeTest',
        'controller.AttendanceViewSmokeTest','service.AttendanceIntegrationTest',
        'controller.NoticeViewSmokeTest')) {
    $log = Join-Path $logRoot ($runner + '.log')
    $ErrorActionPreference = 'Continue'
    & java "-Djavafx.cachedir=$projectRoot/target/javafx-cache" --module-path ($fx -join ';') `
        --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics `
        -cp $classpath "lk.ac.ruhuna.ftms.$runner" *> $log
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $summary += [pscustomobject]@{runner=$runner;exitCode=$code}
    Write-Output "$runner exit=$code"
    Get-Content -LiteralPath $log -Tail 3
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logRoot 'summary.json')
if ($summary.Where({$_.exitCode -ne 0}).Count -gt 0) { throw 'A student or role regression failed; see target/student-test-logs.' }
