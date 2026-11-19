#include <windows.h>
#include <wchar.h>
/* The graphical entry point launches the installed, locally authored M0 driver. */
int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR arguments, int show) {
    (void)instance; (void)previous; (void)arguments; (void)show;
    wchar_t script[32768], shell[32768], command[32768];
    DWORD count = GetModuleFileNameW(NULL, script, 32768);
    if (!count || count >= 32768) return 1;
    wchar_t *slash = wcsrchr(script, L'\\');
    if (!slash || (slash - script) + 18 >= 32768) return 1;
    wcscpy(slash + 1, L"Export-M0.ps1");
    count = GetSystemDirectoryW(shell, 32768);
    const wchar_t *suffix = L"\\WindowsPowerShell\\v1.0\\powershell.exe";
    if (!count || count + wcslen(suffix) >= 32768) return 1;
    wcscat(shell, suffix);
    int used = _snwprintf(command, 32768, L"\"%ls\" -NoLogo -NoProfile -STA -File \"%ls\"", shell, script);
    if (used < 0 || used >= 32768) return 1;
    STARTUPINFOW si = {0}; PROCESS_INFORMATION pi = {0};
    si.cb = sizeof(si);
    if (!CreateProcessW(shell, command, NULL, NULL, FALSE, CREATE_NEW_CONSOLE, NULL, NULL, &si, &pi)) {
        MessageBoxW(NULL, L"Impossibile avviare Windows PowerShell.", L"Studio M0", MB_ICONERROR);
        return 2;
    }
    CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
    return 0;
}
