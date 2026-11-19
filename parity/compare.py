"""Confronto tecnico host di due esportazioni del medesimo bundle."""
import argparse
from fractions import Fraction
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import sys
from zipfile import BadZipFile, ZipFile

import numpy as np

FPS = 30
MAX_FRAMES = 18000
THRESHOLDS = {
    'rgb_frame_mae': 3.0, 'rgb_tile_mae': 12.0, 'loudness_delta_lu': 0.5,
    'loudness_min_lufs': -17, 'loudness_max_lufs': -13, 'true_peak_max_dbtp': -1,
    'pcm_min_correlation': 0.985, 'pcm_max_shift_samples_at_8000_hz': 8,
}
SRT_TIME = re.compile(r'(\d{2}):([0-5]\d):([0-5]\d),(\d{3})')


def clock(value):
    m = SRT_TIME.fullmatch(value)
    if not m:
        raise ValueError('Timestamp SRT non valido')
    h, minutes, sec, ms = map(int, m.groups())
    return ((h * 60 + minutes) * 60 + sec) * 1000 + ms


def cues(path, frames):
    blocks = re.split(r'\n[ \t]*\n', path.read_text(encoding='utf-8-sig').strip())
    result = []
    previous_end = 0
    for i, block in enumerate(blocks, 1):
        lines = block.splitlines()
        if len(lines) not in (3, 4) or lines[0] != str(i):
            raise ValueError('Numerazione o righe cue errate')
        start, end = map(clock, re.split(r'\s+-->\s+', lines[1]))
        if not previous_end <= start < end or end > math.ceil(frames * 1000 / FPS):
            raise ValueError('Cue sovrapposto o fuori timeline')
        result.append([start, end, lines[2:]])
        previous_end = end
    return result


def inspect_ledger(folder, bundle_hash, manifest):
    ledger = json.loads((folder / 'render.json').read_text(encoding='utf-8'))
    if ledger['bundle_sha256'] != bundle_hash or type(ledger['seed']) != int:
        raise ValueError('Identità bundle o seed errati')
    count = ledger['frames']
    if type(count) != int or not 1 <= count <= MAX_FRAMES:
        raise ValueError('Numero frame non supportato')
    last = 0
    scenes = ledger['scenes']
    if not isinstance(scenes, list):
        raise ValueError('Lista scene mancante')
    for row in scenes:
        if not isinstance(row, dict) or not isinstance(row.get('id'), str) or not row['id']:
            raise ValueError('Identità scena mancante')
        start, end = row['start_frame'], row['end_frame']
        if type(start) != int or type(end) != int or start != last or not start < end <= count:
            raise ValueError('Timeline primaria discontinua')
        last = end
    if last != count:
        raise ValueError('Timeline incompleta')
    result_manifest = json.loads((folder / 'project_manifest.json').read_text(encoding='utf-8'))
    if manifest != result_manifest:
        raise ValueError('Manifest diverso da quello del bundle indicato')
    return ledger, cues(folder / 'captions.srt', count)


def cmd(args, *, stderr=False):
    p = subprocess.run(args, capture_output=True, timeout=1200)
    if p.returncode:
        raise ValueError(f'{args[0]} ha restituito {p.returncode}: ' + p.stderr[-2000:].decode(errors='replace'))
    return p.stderr if stderr else p.stdout


def probe(path, *fields):
    return json.loads(cmd(['ffprobe', '-v', 'error', *fields, '-of', 'json', str(path)]))


def metadata(path, expected_frames):
    streams = probe(path, '-show_streams')['streams']
    video = [s for s in streams if s['codec_type'] == 'video']
    audio = [s for s in streams if s['codec_type'] == 'audio']
    if len(audio) != 1 or len(video) != 1:
        raise ValueError('Richiesti un video e un audio master')
    v, a = video[0], audio[0]
    video_fields = ['codec_name', 'width', 'height', 'pix_fmt']
    expected = ['h264', 1080, 1920, 'yuv420p']
    if [v.get(k) for k in video_fields] != expected:
        raise ValueError('Profilo video non conforme')
    if a['codec_name'] != 'aac' or a['sample_rate'] != '48000':
        raise ValueError('Profilo audio non conforme')
    timestamps = probe(path, '-select_streams', 'v:0', '-show_entries', 'frame=best_effort_timestamp')['frames']
    tick = Fraction(v['time_base'])
    if tick > Fraction(1, FPS) or len(timestamps) != expected_frames:
        raise ValueError('Conteggio frame o timebase non conforme')
    for i, frame in enumerate(timestamps):
        if abs(int(frame['best_effort_timestamp']) * tick - Fraction(i, FPS)) > tick / 2:
            raise ValueError(f'PTS variabile o offset al frame {i}')
    duration = Fraction(expected_frames, FPS)
    if abs(Fraction(v['duration']) - duration) > tick or abs(Fraction(a['duration']) - duration) > Fraction(1, FPS):
        raise ValueError('Durata video/audio fuori tolleranza')
    if abs(Fraction(a['start_time'])) > Fraction(1, FPS):
        raise ValueError('Audio fuori sincrono con l’origine')
    return dict(frames=expected_frames, audio_channels=a['channels'], audio_layout=a.get('channel_layout'),
                duration_video=v['duration'], duration_audio=a['duration'], time_base=v['time_base'])


def sample_indices(ledger):
    indices = set()
    for row in ledger['scenes']:
        lo, hi = row['start_frame'], row['end_frame'] - 1
        indices.update((lo, (lo + hi) // 2, hi))
    indices.update(np.linspace(0, ledger['frames'] - 1, 9, dtype=int).tolist())
    indices = sorted(indices)
    if len(indices) <= 64:
        return indices
    return [indices[i] for i in np.linspace(0, len(indices) - 1, 64, dtype=int)]


def samples(path, indices):
    select = '+'.join(f'eq(n\\,{i})' for i in indices)
    graph = 'select=' + select + ',scale=270:480:flags=area,format=rgb24'
    data = cmd(['ffmpeg', '-nostdin', '-v', 'error', '-i', str(path), '-map', '0:v:0',
                '-vf', graph, '-fps_mode', 'passthrough', '-threads', '1', '-f', 'rawvideo', '-'])
    return np.frombuffer(data, np.uint8).reshape(len(indices), 480, 270, 3)


def audio(path):
    base = ['ffmpeg', '-nostdin', '-hide_banner', '-nostats', '-i', str(path), '-map', '0:a:0']
    measurements = cmd(base + ['-af', 'loudnorm=print_format=json', '-f', 'null', '-'], stderr=True).decode()
    obj, _ = json.JSONDecoder().raw_decode('{' + measurements.rsplit('{', 1)[1])
    levels = {'lufs': float(obj['input_i']), 'dbtp': float(obj['input_tp'])}
    if not all(math.isfinite(x) for x in levels.values()):
        raise ValueError('Audio muto o loudness non misurabile')
    raw = cmd(base + ['-ac', '1', '-ar', '8000', '-f', 'f32le', '-'])
    return levels, np.frombuffer(raw, '<f4').astype(np.float64)


def waveform_agreement(a, b):
    n = min(a.size, b.size)
    pad = THRESHOLDS['pcm_max_shift_samples_at_8000_hz']
    if n <= 2 * pad:
        raise ValueError('Audio troppo breve')
    x = a[pad:n-pad]
    x = x - x.mean()
    norm_x = np.linalg.norm(x)
    scores = []
    for shift in range(-pad, pad + 1):
        y = b[pad+shift:n-pad+shift]
        y = y - y.mean()
        norm = norm_x * np.linalg.norm(y)
        corr = float(np.dot(x, y) / norm) if norm else 0
        scores.append((corr, -abs(shift), shift))
    best, _, shift = max(scores)
    return best, shift


def compare(left, right, bundle):
    with bundle.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    with ZipFile(bundle) as z:
        info = z.getinfo('project_manifest.json')
        if info.file_size > 2 * 1024**2 or z.namelist().count(info.filename) != 1:
            raise ValueError('Manifest bundle non valido')
        manifest = json.loads(z.read(info))
    left_plan, left_cues = inspect_ledger(left, digest, manifest)
    right_plan, right_cues = inspect_ledger(right, digest, manifest)
    checks = dict(same_render_plan=left_plan == right_plan, same_cues=left_cues == right_cues)
    paths = [left / 'master.mp4', right / 'master.mp4']
    plans = [left_plan, right_plan]
    meta = [metadata(p, plan['frames']) for p, plan in zip(paths, plans)]
    checks['same_audio_layout'] = all(meta[0][k] == meta[1][k] for k in ['audio_channels', 'audio_layout'])
    indices = [i for i in sample_indices(left_plan) if i < right_plan['frames']]
    l, r = [samples(p, indices) for p in paths]
    worst_frame = 0.0
    worst_tile = 0.0
    for x, y in zip(l, r):
        diff = np.abs(x.astype(np.int16) - y)
        worst_frame = max(worst_frame, float(diff.mean()))
        tiles = diff.reshape(16, 30, 9, 30, 3)
        worst_tile = max(worst_tile, float(tiles.mean(axis=(1, 3, 4)).max()))
    checks['frame_rgb'] = worst_frame <= THRESHOLDS['rgb_frame_mae']
    checks['tile_rgb'] = worst_tile <= THRESHOLDS['rgb_tile_mae']
    (lev_a, pcm_a), (lev_b, pcm_b) = [audio(p) for p in paths]
    checks['loudness'] = all(THRESHOLDS['loudness_min_lufs'] <= s['lufs'] <= THRESHOLDS['loudness_max_lufs']
                             for s in [lev_a, lev_b])
    checks['true_peak'] = all(s['dbtp'] <= THRESHOLDS['true_peak_max_dbtp'] for s in [lev_a, lev_b])
    checks['loudness_delta'] = abs(lev_a['lufs'] - lev_b['lufs']) <= THRESHOLDS['loudness_delta_lu']
    corr, shift = waveform_agreement(pcm_a, pcm_b)
    checks['waveform'] = corr >= THRESHOLDS['pcm_min_correlation']
    return dict(esito='PASS' if all(checks.values()) else 'FAIL', controlli=checks,
                soglie_proposte=THRESHOLDS, approvazione_soglie='PENDENTE', bundle_sha256=digest,
                seed=[p['seed'] for p in plans], frame_campionati=indices,
                errore_rgb_medio_massimo=worst_frame, errore_tessera_massimo=worst_tile,
                livelli=[lev_a, lev_b], correlazione_audio=corr, offset_campioni=shift, stream=meta)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for arg in ['left', 'right', 'bundle']:
        p.add_argument(arg, type=Path)
    args = p.parse_args()
    try:
        result = compare(args.left, args.right, args.bundle)
    except (OSError, KeyError, IndexError, TypeError, ValueError, BadZipFile, RuntimeError, subprocess.TimeoutExpired) as e:
        result = dict(esito='FAIL', errore=str(e), approvazione_soglie='PENDENTE')
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result['esito'] == 'PASS' else 1


if __name__ == '__main__':
    sys.exit(main())
