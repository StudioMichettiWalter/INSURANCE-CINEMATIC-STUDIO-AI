"""Collaudo host offline su un file media. Nessun accesso di rete."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import tempfile
import wave


def timecode(ms):
    seconds, ms = divmod(ms, 1000)
    minutes, sec = divmod(seconds, 60)
    hours, minute = divmod(minutes, 60)
    return f"{hours:02}:{minute:02}:{sec:02},{ms:03}"


def transcribe(cli, model, media, destination):
    lock = json.loads(Path(__file__).with_name("asr.lock.json").read_text())
    with model.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != lock["model"]["sha256"]:
            raise ValueError("Modello non conforme al lock")
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        raise FileExistsError(f"Usare una nuova directory di risultati: {destination}")
    with tempfile.TemporaryDirectory(dir=destination.parent) as tmp:
        folder = Path(tmp)
        audio = folder / "audio.wav"
        if not media.is_file():
            raise ValueError("Selezionare un file audio o video locale")
        subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-protocol_whitelist",
                        "file", "-format_whitelist", "mov,wav,mp3,aac,flac,ogg,matroska",
                        "-i", media.resolve(), "-map", "0:a:0", "-t", "601", "-ar", "16000",
                        "-ac", "1", "-c:a", "pcm_s16le", audio], check=True)
        with wave.open(str(audio)) as wav:
            duration = math.ceil(wav.getnframes() * 1000 / wav.getframerate())
        if duration > 600000 or duration <= 0:
            raise ValueError("Lo spike ASR accetta da 1 ms a 600 s di audio")
        subprocess.run([cli, "-m", model, "-f", audio, "-l", "it", "-t", "2",
                        "-ng", "-oj", "-of", folder / "raw"], check=True)
        raw = json.loads((folder / "raw.json").read_text(encoding="utf-8"))
        cues = []
        blocks = []
        last = 0
        for row in raw["transcription"]:
            start = row["offsets"]["from"]
            end = min(row["offsets"]["to"], duration)
            label = row["text"].strip()
            if not label:
                continue
            if not (last <= start < end):
                raise ValueError("Intervallo ASR non valido, verificare il parlato")
            last = end
            cues.append(dict(start_ms=start, end_ms=end, text=label))
            blocks.append(f"{len(cues)}\n{timecode(start)} --> {timecode(end)}\n{label}\n")
        if not cues:
            raise ValueError("Nessun segmento trascritto")
        out = dict(schema_version=1, engine=lock["engine"]["version"],
                   model_sha256=lock["model"]["sha256"], language="it",
                   audio_duration_ms=duration, cues=cues)
        (folder / "result.json").write_text(json.dumps(out, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
        (folder / "result.srt").write_text("\n".join(blocks), encoding="utf-8")
        destination.mkdir(exist_ok=False)
        try:
            for name in ["result.json", "result.srt", "raw.json"]:
                (folder / name).replace(destination / name)
        except BaseException:
            for child in destination.iterdir():
                child.unlink()
            destination.rmdir()
            raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for field in ("cli", "model", "media", "destination"):
        parser.add_argument(field, type=Path)
    args = parser.parse_args()
    transcribe(**vars(args))
