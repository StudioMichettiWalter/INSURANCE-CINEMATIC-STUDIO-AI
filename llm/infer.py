"""Harness CLI M0 locale. Nessun download o tool messo a disposizione del modello."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parent
WORD = re.compile(r"[^\W_]+(?:['’][^\W_]+)*", re.UNICODE)
MODEL_HASH = json.loads((ROOT / "dependencies.lock.json").read_text())["model"]["sha256"]


def candidates(transcript):
    if len(transcript) > 2500 or not transcript.strip():
        raise ValueError("Serve un segmento di parlato da 1 a 2500 caratteri")
    words = list(WORD.finditer(transcript))
    if not words or len(words) > 240:
        raise ValueError("Servono da 1 a 240 parole per segmento")
    tokens = list(dict.fromkeys(w.group() for w in words))
    titles = []
    for index in range(len(words)):
        for count in range(min(2, len(words)), 6):
            if index + count > len(words):
                break
            excerpt = transcript[words[index].start():words[index + count - 1].end()]
            if len(excerpt) <= 55 and not any(c in excerpt for c in '\n\r.!?'):
                titles.append(excerpt)
    return tokens, list(dict.fromkeys(titles or tokens))


def infer(engine, model, transcript):
    with model.open("rb") as handle:
        digest = hashlib.file_digest(handle, "sha256").hexdigest()
    if digest != MODEL_HASH:
        raise ValueError("Hash modello diverso dal lockfile")
    tokens, titles = candidates(transcript)
    schema = {"type": "object", "properties": {
        "keyword": {"type": "string", "enum": tokens},
        "title": {"type": "string", "enum": titles}},
        "required": ["keyword", "title"], "additionalProperties": False}
    system = (ROOT / "prompt.it.txt").read_text(encoding="utf-8").strip()
    # Escape ChatML controls within user data before applying this model's chat format.
    data = json.dumps({"trascrizione": transcript}, ensure_ascii=False).replace("<", r"\u003c").replace(">", r"\u003e")
    prompt = f"<|im_start|>system\n{system}<|im_end|>\n<|im_start|>user\n{data}<|im_end|>\n<|im_start|>assistant\n"
    with tempfile.TemporaryDirectory() as directory:
        folder = Path(directory)
        (folder / "schema.json").write_text(json.dumps(schema), encoding="utf-8")
        (folder / "prompt.txt").write_text(prompt, encoding="utf-8")
        args = [str(engine.resolve()), "-m", str(model.resolve()), "-f", str(folder / "prompt.txt"),
                "-jf", str(folder / "schema.json"), "--no-conversation", "--no-display-prompt",
                "--no-escape", "--no-warmup", "--temp", "0", "--seed", "42",
                "-n", "120", "-c", "2048", "-t", "2", "-ngl", "0", "--simple-io"]
        start = time.monotonic()
        result = subprocess.run(args, encoding="utf-8", capture_output=True,
                                stdin=subprocess.DEVNULL, cwd=directory, timeout=120, check=True)
        elapsed = time.monotonic() - start
    # CLI b6000 prints its own marker after generation.
    raw = result.stdout.strip().removesuffix("[end of text]").strip()
    answer = json.loads(raw)
    if set(answer) != {"title", "keyword"} or answer["keyword"] not in tokens or answer["title"] not in titles:
        raise ValueError("Selezione estrattiva non valida")
    return {**answer, "role": "Video Editor", "status": "PROPOSED", "model": "Qwen2.5-0.5B-Instruct-Q4_K_M",
            "model_sha256": digest, "engine": "llama.cpp b6000", "prompt_sha256": hashlib.sha256(system.encode()).hexdigest(),
            "transcript_sha256": hashlib.sha256(transcript.encode()).hexdigest(), "seconds": round(elapsed, 3),
            "sources": ["trascrizione locale"], "decision": "Proposta estrattiva per titolo e sottotitoli, da approvare."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    for key in ["engine", "model", "input", "output"]:
        parser.add_argument("--" + key, required=True, type=Path)
    args = parser.parse_args()
    try:
        answer = infer(args.engine, args.model, args.input.read_text(encoding="utf-8").strip())
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile("w", dir=args.output.parent, delete=False, encoding="utf-8") as out:
            json.dump(answer, out, ensure_ascii=False, indent=2)
            out.write("\n")
        os.replace(out.name, args.output)
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        parser.exit(1, f"Inferenza interrotta: {error}\n")
