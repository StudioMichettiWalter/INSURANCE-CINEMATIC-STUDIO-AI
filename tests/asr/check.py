"""Misura il testo normalizzato e valida gli intervalli contro la fixture."""
import argparse
import json
from pathlib import Path
import re
import sys
import wave


def tokens(text):
    return re.findall(r"\w+", text.casefold())


def edits(ref, hyp):
    prev = list(range(len(hyp) + 1))
    for i, word in enumerate(ref, 1):
        cur = [i]
        for j, pred in enumerate(hyp, 1):
            cur.append(min(cur[-1] + 1, prev[j] + 1, prev[j-1] + int(word != pred)))
        prev = cur
    return prev[-1]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result", type=Path)
    args = parser.parse_args()
    case = Path(__file__).parent
    ref = tokens((case / "riferimento.txt").read_text(encoding="utf-8"))
    result = json.loads(args.result.read_text(encoding="utf-8"))
    with wave.open(str(case / "voce.wav")) as wav:
        limit = (wav.getnframes() * 1000 + wav.getframerate() - 1) // wav.getframerate()
    valid = result["audio_duration_ms"] == limit and len(result["cues"]) > 0
    end = 0
    for cue in result["cues"]:
        valid = valid and end <= cue["start_ms"] < cue["end_ms"] <= limit
        end = cue["end_ms"]
    hyp = tokens(" ".join(cue["text"] for cue in result["cues"]))
    distance = edits(ref, hyp)
    print(json.dumps(dict(reference_words=len(ref), edits=distance,
                          word_error_rate=distance/len(ref), cue_bounds_valid=valid,
                          exact_match=distance == 0)))
    sys.exit(0 if distance == 0 and valid else 1)
