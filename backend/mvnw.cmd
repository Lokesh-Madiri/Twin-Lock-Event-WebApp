@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script for Windows
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET "APP_BASE_NAME=%~n0") ELSE (SET "APP_BASE_NAME=%__MVNW_ARG0_NAME__%")
@SET "APP_HOME=%~dp0"

@REM Resolve any relative path components
@FOR /F "delims=" %%i IN ("%APP_HOME%") DO @SET APP_HOME=%%~fi

@SET "MVN_CMD=mvn"
@SET "WRAPPER_DIR=%APP_HOME%.mvn\wrapper"
@SET "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"
@SET "WRAPPER_PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties"

@REM Find JAVA_HOME
@IF NOT "%JAVA_HOME%"=="" (
    @IF EXIST "%JAVA_HOME%\bin\java.exe" (
        @SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    )
)
@IF "%JAVA_EXE%"=="" (SET "JAVA_EXE=java")

@REM --- Try downloading wrapper jar if not present ---
@IF EXIST "%WRAPPER_JAR%" GOTO runMvnWrapper

@SET "DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
@echo Downloading Maven Wrapper...

@IF NOT "%MVNW_VERBOSE%"=="true" (
    @powershell -Command "&{"                                            ^
      "$webclient = New-Object System.Net.WebClient;"                   ^
      "if ($env:MVNW_USERNAME -And $env:MVNW_PASSWORD) {"              ^
      "  $webclient.Credentials = New-Object System.Net.NetworkCredential($env:MVNW_USERNAME, $env:MVNW_PASSWORD);" ^
      "}"                                                                ^
      "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
      "$webclient.DownloadFile('%DOWNLOAD_URL%', '%WRAPPER_JAR%');"     ^
    "}"
)
@IF "%ERRORLEVEL%"=="0" GOTO runMvnWrapper
@echo Failed to download Maven wrapper JAR.
@echo Please install Maven manually: https://maven.apache.org/download.cgi
@exit /b 1

:runMvnWrapper
@"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %* 2>NUL
@IF "%ERRORLEVEL%"=="0" GOTO end

@REM Wrapper jar didn't work — fallback to direct Maven download via powershell
@echo Bootstrapping Maven via PowerShell...
@powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$url='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip';" ^
    "$dest='%TEMP%\apache-maven-3.9.6-bin.zip';" ^
    "$mvnDir='%TEMP%\apache-maven-3.9.6';" ^
    "if (-not (Test-Path $mvnDir)) {" ^
    "  Write-Host 'Downloading Apache Maven 3.9.6...';" ^
    "  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
    "  (New-Object Net.WebClient).DownloadFile($url, $dest);" ^
    "  Expand-Archive -Path $dest -DestinationPath '%TEMP%' -Force;" ^
    "}" ^
    "$env:PATH = \"$mvnDir\bin;\" + $env:PATH;" ^
    "& \"$mvnDir\bin\mvn.cmd\" %*"

:end
