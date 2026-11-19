Unicode true
Name "Insurance Cinematic Studio — prova M0"
!ifndef STUDIO_SETUP
  !define STUDIO_SETUP "build/Insurance-Studio-M0-Setup.exe"
!endif
OutFile "${STUDIO_SETUP}"
RequestExecutionLevel user
InstallDir "$LOCALAPPDATA\InsuranceCinematicStudioM0"
ShowInstDetails show
ShowUninstDetails show
Page directory
Page instfiles
UninstPage uninstConfirm
UninstPage instfiles
Section
  SetOutPath "$INSTDIR"
  File "build/StudioM0.exe"
  File "build/scene-frames.exe"
  File "Export-M0.ps1"
  File "LocalAi.ps1"
  File "Quality.ps1"
  File "PortableProject.ps1"
  File "windows.lock.json"
  File "Prepare-Runtime.ps1"
  File "../llm/prompt.it.txt"
  File "../llm/dependencies.lock.json"
  File "../asr/asr.lock.json"
  File "../source/insurance-cinematic-studio/SKILL.md"
  File "../docs/regole-prodotto.json"
  File "README.md"
  SetOutPath "$INSTDIR\fonts"
  File "../brand/Inter.ttf"
  File "../brand/OFL.txt"
  SetOutPath "$INSTDIR"
  WriteUninstaller "$INSTDIR\Disinstalla.exe"
  CreateShortcut "$SMPROGRAMS\Insurance Cinematic Studio M0.lnk" "$INSTDIR\StudioM0.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\InsuranceCinematicStudioM0" "DisplayName" "Insurance Cinematic Studio — prova M0"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\InsuranceCinematicStudioM0" "DisplayVersion" "0.1.0-m0.4"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\InsuranceCinematicStudioM0" "UninstallString" '"$INSTDIR\Disinstalla.exe"'
SectionEnd
Section "Uninstall"
  Delete "$SMPROGRAMS\Insurance Cinematic Studio M0.lnk"
  Delete "$INSTDIR\StudioM0.exe"
  Delete "$INSTDIR\scene-frames.exe"
  Delete "$INSTDIR\windows.lock.json"
  Delete "$INSTDIR\Prepare-Runtime.ps1"
  Delete "$INSTDIR\LocalAi.ps1"
  Delete "$INSTDIR\Quality.ps1"
  Delete "$INSTDIR\PortableProject.ps1"
  Delete "$INSTDIR\prompt.it.txt"
  Delete "$INSTDIR\dependencies.lock.json"
  Delete "$INSTDIR\asr.lock.json"
  Delete "$INSTDIR\SKILL.md"
  Delete "$INSTDIR\regole-prodotto.json"
  Delete "$INSTDIR\Export-M0.ps1"
  Delete "$INSTDIR\README.md"
  Delete "$INSTDIR\fonts\Inter.ttf"
  Delete "$INSTDIR\fonts\OFL.txt"
  RMDir "$INSTDIR\fonts"
  Delete "$INSTDIR\Disinstalla.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\InsuranceCinematicStudioM0"
SectionEnd
