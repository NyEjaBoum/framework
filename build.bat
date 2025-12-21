@echo off
setlocal enabledelayedexpansion

REM build.bat - compile le framework, crée framework.jar, copie dans Tomcat lib
REM et crée le dossier d'uploads utilisé par l'application.

echo === BUILD FRAMEWORK ===

REM Configuration
set "ROOT_DIR=%~dp0"
set "APP_NAME=test"
set "TOMCAT_HOME=C:\Program Files\Apache Software Foundation\Tomcat 10.1"
set "TOMCAT_LIB=%TOMCAT_HOME%\lib"

set "FRAMEWORK_SRC_DIR=%ROOT_DIR%framework\src\framework"
set "BUILD_DIR=%ROOT_DIR%build\classes"
set "FRAMEWORK_JAR=%ROOT_DIR%framework\framework.jar"
set "JAKARTA_JAR=%ROOT_DIR%framework\lib\jakarta.servlet-api-5.0.0.jar"

echo ROOT_DIR=%ROOT_DIR%
echo APP_NAME=%APP_NAME%
echo TOMCAT_HOME=%TOMCAT_HOME%
echo TOMCAT_LIB=%TOMCAT_LIB%

REM Créer le répertoire de build
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

REM Lister les sources du framework
if exist "%ROOT_DIR%build\framework-sources.txt" del "%ROOT_DIR%build\framework-sources.txt"
dir /b /s "%FRAMEWORK_SRC_DIR%\*.java" > "%ROOT_DIR%build\framework-sources.txt" 2>nul

REM Vérifier si des sources existent
for %%A in ("%ROOT_DIR%build\framework-sources.txt") do (
    if %%~zA==0 (
        echo Aucune source framework trouvee dans %FRAMEWORK_SRC_DIR%
        goto :end
    )
)

echo [1/3] Compilation des sources framework...
if exist "%JAKARTA_JAR%" (
    javac -d "%BUILD_DIR%" -cp "%JAKARTA_JAR%" @"%ROOT_DIR%build\framework-sources.txt"
) else (
    echo Avertissement: %JAKARTA_JAR% introuvable, compilation sans dependance servlet-api.
    javac -d "%BUILD_DIR%" @"%ROOT_DIR%build\framework-sources.txt"
)

if %ERRORLEVEL% neq 0 (
    echo ERREUR: compilation du framework a echoue
    pause
    exit /b 1
)
del "%ROOT_DIR%build\framework-sources.txt"

echo [2/3] Creation du JAR : %FRAMEWORK_JAR%
if not exist "%ROOT_DIR%framework" mkdir "%ROOT_DIR%framework"
jar cvf "%FRAMEWORK_JAR%" -C "%BUILD_DIR%" . >nul 2>&1

if not exist "%FRAMEWORK_JAR%" (
    echo ERREUR: Le JAR n'a pas ete cree
    pause
    exit /b 1
)

REM Copier dans Tomcat global lib si possible
if exist "%TOMCAT_LIB%" (
    echo Copie du framework.jar vers %TOMCAT_LIB%\
    copy /Y "%FRAMEWORK_JAR%" "%TOMCAT_LIB%\" >nul
    echo √ Copie : %TOMCAT_LIB%\framework.jar
) else (
    echo Avertissement: dossier Tomcat lib introuvable: %TOMCAT_LIB%. JAR laisse dans %FRAMEWORK_JAR%
)

REM [3/3] Créer dossier d'upload
echo [3/3] Configuration du dossier d'upload...

set "UPLOAD_DIR=%TOMCAT_HOME%\webapps\%APP_NAME%\uploads"

echo Creation du dossier d'upload : %UPLOAD_DIR%

if not exist "%UPLOAD_DIR%" (
    mkdir "%UPLOAD_DIR%"
    if %ERRORLEVEL% equ 0 (
        echo Dossier cree : %UPLOAD_DIR%
    ) else (
        echo ERREUR: Impossible de creer le dossier %UPLOAD_DIR%
    )
) else (
    echo Dossier existe deja : %UPLOAD_DIR%
)

echo.
echo === BUILD FRAMEWORK TERMINE ===
echo.
echo Prochaines etapes :
echo   1. compile.bat    # compile et deploie le projet test
echo   2. Redemarrer Tomcat si necessaire
echo   3. Tester l'upload sur http://localhost:8080/%APP_NAME%/etudiant/upload-form

:end
pause