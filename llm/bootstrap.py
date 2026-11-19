"""Scarica dipendenze bloccate per l'harness M0 su Linux o Windows x64."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import tempfile
import urllib.request
import zipfile

LOCK = json.loads(Path(__file__).with_name("dependencies.lock.json").read_text())


def fetch(item, target):
    def valid(path):
        with path.open("rb") as handle:
            return hashlib.file_digest(handle, "sha256").hexdigest() == item["sha256"]

    if target.exists() and valid(target):
        return
    with tempfile.NamedTemporaryFile(dir=target.parent, delete=False) as temp:
        name = Path(temp.name)
    try:
        with urllib.request.urlopen(item["url"], timeout=120) as response, name.open("wb") as stream:
            shutil.copyfileobj(response, stream)
        if not valid(name):
            raise ValueError(f"SHA-256 errato per {target.name}")
        os.replace(name, target)
    finally:
        name.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("dest", type=Path)
    args = parser.parse_args()
    host = platform.system().lower()
    if host not in ("linux", "windows") or platform.machine().lower() not in ("x86_64", "amd64"):
        parser.error("L'harness richiede Linux x64 o Windows x64.")
    args.dest.mkdir(parents=True, exist_ok=True)
    model = args.dest / "qwen.gguf"
    archive = args.dest / f"llama-b6000-{host}.zip"
    fetch(LOCK["model"], model)
    fetch(LOCK[host], archive)
    # Published upstream archive is SHA-256 pinned and developer-controlled.
    with zipfile.ZipFile(archive) as zipped:
        zipped.extractall(args.dest / "engine")
    matches = list((args.dest / "engine").rglob("llama-cli.exe" if host == "windows" else "llama-cli"))
    if len(matches) != 1:
        raise RuntimeError("Contenuto archivio inatteso.")
    matches[0].chmod(0o755)
    print(f"Engine: {matches[0].resolve()}\nModello: {model.resolve()}")


if __name__ == "__main__":
    main()
