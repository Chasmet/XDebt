@echo off
setlocal
if exist "%~dp0gradle\wrapper\gradle-wrapper.jar" (
  java -cp "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) else (
  gradle %*
)
