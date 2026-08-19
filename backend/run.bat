@echo off
cd /d "%~dp0"
set MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT
mvn -s "%~dp0maven-settings.xml" spring-boot:run %*
