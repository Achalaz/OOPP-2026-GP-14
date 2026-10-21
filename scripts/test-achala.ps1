param(
    [string]$Maven = 'mvn',
    [string]$Repository = "$env:USERPROFILE/.m2/repository",
    [switch]$IncludeDatabase,
    [switch]$IncludeJavaFx,
    [switch]$AllRegressions,
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
& $Maven @mavenArguments package
if ($LASTEXITCODE -ne 0) { throw 'Maven package failed.' }
if ($IncludeDatabase) {
    & $Maven @mavenArguments '-Dtest=AcademicMySqlIT' test
    if ($LASTEXITCODE -ne 0) { throw 'Academic MySQL tests failed.' }
}
if (!$IncludeJavaFx -and !$AllRegressions) { return }
$logRoot = Join-Path $projectRoot 'target/achala-regression-logs'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$fx = @('base','graphics','controls','fxml') | ForEach-Object {
    "$Repository/org/openjfx/javafx-$_/25/javafx-$_-25-win.jar"
}
$classpath = "target/test-classes;target/classes;$Repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar"
$runners = @('controller.AcademicViewsSmokeTest','controller.ResultsViewSmokeTest',
    'controller.MarksEntryIntegrationTest','controller.LecturerNavigationSmokeTest',
    'controller.AcademicAcceptanceSmokeTest')
if ($AllRegressions) {
    $runners = @('service.ResultServiceTest','service.ResultPublicationTest','service.ResultPublicationDatabaseTest',
        'controller.ResultsViewSmokeTest','dao.ResultDatabaseSmokeTest','service.ProfileServiceTest',
        'service.ProfilePictureServiceTest','service.CourseMaterialServiceTest','service.NoticeServiceTest',
        'dao.NoticeDatabaseSmokeTest','controller.NoticeViewSmokeTest','service.LecturerAccessSmokeTest',
        'controller.LecturerViewSmokeTest','controller.LecturerNavigationSmokeTest','controller.MarksEntryIntegrationTest',
        'service.AttendanceIntegrationTest','controller.AttendanceViewSmokeTest','controller.TechnicalOfficerDashboardSmokeTest',
        'dao.ProfileDatabaseSmokeTest','dao.ProfilePictureDatabaseSmokeTest','controller.ProfileViewSmokeTest',
        'controller.AcademicViewsSmokeTest','controller.AcademicAcceptanceSmokeTest')
}
$summary = @()
foreach ($runner in $runners) {
    $arguments = @()
    if ($runner -in @('dao.ResultDatabaseSmokeTest','controller.MarksEntryIntegrationTest',
        'dao.ProfileDatabaseSmokeTest','dao.ProfilePictureDatabaseSmokeTest')) { $arguments = @('--rollback-write') }
    if ($runner -eq 'dao.NoticeDatabaseSmokeTest') { $arguments = @('--temporary-table') }
    $log = Join-Path $logRoot ($runner + '.log')
    $ErrorActionPreference = 'Continue'
    & java "-Djavafx.cachedir=$projectRoot/target/javafx-cache" --module-path ($fx -join ';') `
        --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics `
        -cp $classpath "lk.ac.ruhuna.ftms.$runner" @arguments *> $log
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $summary += [pscustomobject]@{runner=$runner;exitCode=$code}
    Write-Output "$runner exit=$code"
    Get-Content -LiteralPath $log -Tail 3
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logRoot 'summary.json')
if ($summary.Where({$_.exitCode -ne 0}).Count -gt 0) { throw 'Some explicit regressions failed; see target/achala-regression-logs/summary.json.' }
