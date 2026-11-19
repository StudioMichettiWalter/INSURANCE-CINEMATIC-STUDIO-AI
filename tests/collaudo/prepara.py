"""Crea il kit sintetico per M0.3 a partire dalle fixture del repository."""
import argparse
from hashlib import file_digest
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    with tempfile.TemporaryDirectory(prefix="studio-collaudo-") as temp:
        kit = Path(temp)
        clip = kit / "clip.mp4"
        subprocess.run([
            "ffmpeg", "-nostdin", "-v", "error", "-f", "lavfi", "-i",
            "testsrc2=s=360x640:r=30:d=5", "-c:v", "libx264", "-threads", "2",
            "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(clip),
        ], check=True)
        shutil.copyfile(ROOT / "tests/asr/voce.wav", kit / "voce.wav")
        shutil.copyfile(ROOT / "qa/asr/accettata/result.srt", kit / "riferimento.srt")
        files = {}
        for name in ("clip.mp4", "voce.wav", "riferimento.srt"):
            with (kit / name).open("rb") as stream:
                files[name] = file_digest(stream, "sha256").hexdigest()
        fixture = {"id": "m0.3-media-italiano", "frames": 99, "files": files}
        (kit / "fixture.json").write_text(json.dumps(fixture, indent=2) + "\n", encoding="utf-8")
        sources = {
            "Windows-Collaudo.ps1": ROOT / "tests/collaudo/Windows-Collaudo.ps1",
            "ISTRUZIONI.md": ROOT / "docs/COLLAUDO-RAPIDO.md",
            "compare_m0.py": ROOT / "parity/compare_m0.py",
            "compare.py": ROOT / "parity/compare.py",
            "requirements-host.lock": ROOT / "parity/requirements-host.lock",
        }
        with ZipFile(args.output, "x", ZIP_DEFLATED) as bundle:
            for path in sorted(kit.iterdir()):
                bundle.write(path, path.name)
            for name, path in sources.items():
                bundle.write(path, name)


if __name__ == "__main__":
    main()
