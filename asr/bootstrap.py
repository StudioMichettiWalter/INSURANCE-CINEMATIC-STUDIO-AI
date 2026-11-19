"""Build host whisper-cli; model downloads are explicit and content checked."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile
from tempfile import TemporaryDirectory
from urllib.request import urlopen
import shutil

LOCK = json.loads(Path(__file__).with_name("asr.lock.json").read_text())


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def fetch(entry, dest):
    if dest.is_file() and digest(dest) == entry["sha256"]:
        return
    with TemporaryDirectory(dir=dest.parent) as tmp:
        part = Path(tmp) / "download"
        with urlopen(entry["url"], timeout=120) as source, part.open("wb") as out:
            shutil.copyfileobj(source, out)
        if digest(part) != entry["sha256"]:
            raise ValueError(f"SHA-256 inatteso per {dest.name}")
        part.replace(dest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    root = args.directory.resolve()
    root.mkdir(parents=True, exist_ok=True)
    archive = root / "src.tgz"
    fetch(LOCK["model"], root / "model.bin")
    fetch(LOCK["engine"], archive)
    with tarfile.open(archive) as source:
        source.extractall(root, filter="data")
    build = root / "build"
    subprocess.run(["cmake", "-S", root / "whisper.cpp-1.7.6", "-B", build,
                    "-DCMAKE_BUILD_TYPE=Release", "-DBUILD_SHARED_LIBS=OFF",
                    "-DWHISPER_BUILD_TESTS=OFF", "-DGGML_NATIVE=OFF"], check=True)
    subprocess.run(["cmake", "--build", build, "--config", "Release",
                    "--parallel", "2", "--target", "whisper-cli"], check=True)


if __name__ == "__main__":
    main()
