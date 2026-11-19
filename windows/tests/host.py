"""Test del driver PowerShell su host. Richiede compilatore C, FFmpeg e PowerShell 7."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]

def run(args, **kwargs):
    return subprocess.run([str(value) for value in args], check=True, capture_output=True, text=True, **kwargs)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pwsh", required=True)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix="studio-windows-qa-") as directory:
        temp = Path(directory)
        for name in ["Export-M0.ps1", "LocalAi.ps1", "Quality.ps1", "PortableProject.ps1"]:
            shutil.copy(ROOT / "windows" / name, temp / name)
        shutil.copytree(ROOT / "brand", temp / "fonts")
        run(["cc", "-O2", "-std=c99", ROOT / "core/scene.c", ROOT / "core/render.c",
             "-lm", "-o", temp / "scene-frames.exe"])
        config = temp / "runtime.json"
        config.write_text(json.dumps({"Ffmpeg": shutil.which("ffmpeg"), "Ffprobe": shutil.which("ffprobe")}))
        clip = temp / "clip.mp4"
        run(["ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i",
             "testsrc2=s=360x640:r=30:d=5", "-c:v", "libx264", "-threads", "2",
             "-pix_fmt", "yuv420p", clip])
        master = temp / "master.mp4"
        invocation = [args.pwsh, "-NoProfile", "-File", temp / "Export-M0.ps1",
                      "-Clip", clip, "-Voce", ROOT / "tests/asr/voce.wav",
                      "-Sottotitoli", ROOT / "qa/asr/accettata/result.srt",
                      "-Destinazione", master, "-RuntimeConfig", config]
        run(invocation)
        quality = run([args.pwsh, "-NoProfile", "-File", ROOT / "windows/tests/quality.ps1",
                       "-Ffmpeg", shutil.which("ffmpeg"), "-WorkingDirectory", temp / "negative",
                       "-AcceptedMaster", master])
        checks = json.loads(quality.stdout)
        # File già presente: l'export deve fermarsi conservando byte ed export precedente.
        original = master.read_bytes()
        repeated = subprocess.run(list(map(str, invocation)), capture_output=True, text=True)
        assert repeated.returncode != 0 and master.read_bytes() == original
        # Narrazione muta: rifiuto prima del rendering, senza nuovo master.
        short = temp / "short.srt"
        short.write_text("1\n00:00:00,000 --> 00:00:00,800\nVoce di test.\n")
        silent = list(map(str, invocation))
        silent[silent.index("-Voce")+1] = str(temp / "negative/silent.wav")
        silent[silent.index("-Sottotitoli")+1] = str(short)
        silent[silent.index("-Destinazione")+1] = str(temp / "blocked.mp4")
        failure = subprocess.run(silent, capture_output=True, text=True)
        assert failure.returncode != 0 and not (temp / "blocked.mp4").exists()
        checks["existing_export_preserved"] = True
        checks["silent_export_blocked"] = True
        checks["platform"] = "Host PowerShell 7. Windows nativo NON ESEGUITO."
        print(json.dumps(checks, indent=2))

if __name__ == "__main__":
    main()
