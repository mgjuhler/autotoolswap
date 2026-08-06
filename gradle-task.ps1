param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Tasks = @('build'))
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'
Set-Location 'C:\Users\mgjuh\autotoolswap'
& .\gradlew.bat @Tasks
exit $LASTEXITCODE
