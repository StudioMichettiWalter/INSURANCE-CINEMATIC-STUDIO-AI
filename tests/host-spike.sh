#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
: "${ASR_CLI:?percorso whisper-cli}" "${ASR_MODEL:?modello whisper}" "${LLM_CLI:?percorso llama-cli}" "${LLM_MODEL:?modello Qwen}"
mkdir -p build
cc -O2 -std=c99 core/scene.c core/render.c -lm -o build/scene-render
tmpdir=$(mktemp -d build/test-XXXXXXXX)
ffmpeg -nostdin -v error -f lavfi -i testsrc2=s=360x640:r=30:d=12 -c:v libx264 -threads 2 -pix_fmt yuv420p "$tmpdir/clip.mp4"
echo "Prove in $tmpdir/job"
python3 scripts/spike.py --clip "$tmpdir/clip.mp4" --voice tests/asr/voce.wav --reference tests/asr/riferimento.txt --whisper "$ASR_CLI" --asr-model "$ASR_MODEL" --llama "$LLM_CLI" --llm-model "$LLM_MODEL" --renderer build/scene-render --job "$tmpdir/job"
