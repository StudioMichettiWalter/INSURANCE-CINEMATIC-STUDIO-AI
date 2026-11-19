"""Collaudo host. Input locali espliciti. Crea una nuova directory per ogni export."""
import argparse
from decimal import Decimal, ROUND_CEILING
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
FPS = 30

def run(args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)

def probe(path, *args):
    return json.loads(run(["ffprobe", "-v", "error", "-protocol_whitelist", "file,pipe", *args, "-of", "json", path],
                          capture_output=True).stdout)

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def captions(srt, keyword, duration):
    header = """[Script Info]
ScriptType: v4.00+
PlayResX: 1080
PlayResY: 1920
WrapStyle: 2
[V4+ Styles]
Format: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding
Style: Main,Inter,56,&H00FFFFFF,&H00FFFFFF,&H0016280A,&H80000000,-1,0,0,0,100,100,0,0,1,3,0,2,96,96,260,1
[Events]
Format: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
"""
    def cs(value):
        h, m, s, ms = map(int, re.split('[:,]', value))
        if m >= 60 or s >= 60:
            raise ValueError("Timestamp SRT fuori intervallo")
        return (3600*h + 60*m + s)*100 + ms//10
    def clock(t):
        return f"{t//360000}:{t//6000%60:02}:{t//100%60:02}.{t%100:02}"
    import textwrap
    events = []
    last = 0
    for block in re.split(r'\n\s*\n', srt.strip()):
        parts = block.splitlines()
        if len(parts) < 3 or not parts[0].isdigit():
            raise ValueError("Blocco SRT incompleto")
        match = re.fullmatch(r'(\d{2}:\d{2}:\d{2},\d{3}) --> (\d{2}:\d{2}:\d{2},\d{3})', parts[1])
        if not match:
            raise ValueError("Timecode SRT non valido")
        start, end = map(cs, match.groups())
        if start < last or end <= start or end > math.ceil(duration*100)+1:
            raise ValueError("Cue sovrapposto o esterno alla narrazione")
        last = end
        raw = ' '.join(' '.join(parts[2:]).split())
        if any(c in raw for c in '{}\\') or not raw:
            raise ValueError("Testo vuoto o controllo ASS non ammesso")
        lines = textwrap.wrap(raw, width=27, break_long_words=False, break_on_hyphens=False)
        if len(lines)>2 or any(len(line)>27 for line in lines):
            raise ValueError("Suddividere il cue in frasi più brevi con timestamp ASR")
        styled = r'\N'.join(lines)
        if keyword:
            styled = re.sub(r'(?<!\w)'+re.escape(keyword)+r'(?!\w)', lambda m:r'{\c&H77C7E6&}'+m[0]+r'{\c&HFFFFFF&}', styled, flags=re.I)
        events.append(f"Dialogue: 0,{clock(start)},{clock(end)},Main,,0,0,0,,{styled}")
    return header+'\n'.join(events)+'\n'

def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ('clip','voice','srt','renderer','out'):
        p.add_argument('--'+name, type=Path, required=True)
    p.add_argument('--keyword', default='')
    a = p.parse_args()
    clip, voice, srt, renderer, out = [getattr(a,k).resolve() for k in ('clip','voice','srt','renderer','out')]
    if out.exists():
        p.error("La destinazione deve essere nuova")
    start = time.monotonic()
    audio = probe(voice, '-select_streams','a:0','-show_streams')['streams'][0]
    seconds = Decimal(audio['duration'])
    if not seconds.is_finite() or not 0 < seconds <= 600:
        raise ValueError("La prova host supporta da un frame a 600 s di voce")
    frames = int((seconds*FPS).to_integral_value(rounding=ROUND_CEILING))
    video = probe(clip,'-select_streams','v:0','-show_streams')['streams'][0]
    if Decimal(video['duration']) + Decimal(1)/FPS < seconds:
        raise ValueError("La clip è più breve della narrazione. Fornire un taglio sufficiente")
    out.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(dir=out.parent) as temp:
        folder = Path(temp)
        ass = captions(srt.read_text(encoding='utf-8-sig'),a.keyword,float(seconds))
        (folder/'caption.ass').write_text(ass,encoding='utf-8')
        shutil.copytree(ROOT/'brand',folder/'fonts')
        base = ['ffmpeg','-nostdin','-hide_banner']
        loud = 'loudnorm=I=-16:TP=-1.5:LRA=8'
        scan = run(base+['-protocol_whitelist','file,pipe','-i',voice,'-map','0:a:0','-af',loud+':print_format=json','-f','null','-'],capture_output=True)
        measure = json.JSONDecoder().raw_decode('{'+scan.stderr.decode().rsplit('{',1)[1])[0]
        for key in ['input_i','input_tp','input_lra','input_thresh','target_offset']:
            if not math.isfinite(float(measure[key])):
                raise ValueError("Livello della narrazione non misurabile")
        normal = loud+':linear=true'+''.join(':'+key+'='+measure[src] for key,src in [
            ('measured_I','input_i'),('measured_TP','input_tp'),('measured_LRA','input_lra'),
            ('measured_thresh','input_thresh'),('offset','target_offset')])
        graph = ('[0:v:0]fps=30,scale=1080:1920:force_original_aspect_ratio=decrease,'
                 'pad=1080:1920:(ow-iw)/2:(oh-ih)/2:color=0x0A1628,setsar=1,tpad=stop_duration=0.04:stop_mode=clone[clip];'
                 '[1:v]scale=1080:1920[overlay];[clip][overlay]overlay=shortest=1,'
                 'ass=caption.ass:fontsdir=fonts,format=yuv420p[v]')
        with subprocess.Popen([str(renderer),'1080','1920',str(frames)],stdout=subprocess.PIPE) as scene:
            try:
                run(base+['-loglevel','error','-filter_complex_threads','2','-protocol_whitelist','file,pipe','-i',clip,'-f','rawvideo',
                          '-pixel_format','rgba','-video_size','1080x1920','-framerate','30','-i','pipe:0','-protocol_whitelist','file,pipe','-i',voice,
                          '-filter_complex',graph,'-map','[v]','-map','2:a:0','-af',normal,
                          '-c:v','libx264','-threads','2','-preset','veryfast','-crf','18','-profile:v','high',
                          '-c:a','aac','-ar','48000','-b:a','192k','-movflags','+faststart','master.mp4'],
                          cwd=folder,stdin=scene.stdout)
            finally:
                scene.stdout.close()
            if scene.wait():
                raise RuntimeError("Renderer interrotto")
        master = folder/'master.mp4'
        streams = probe(master,'-count_frames','-show_streams')['streams']
        v, au = streams
        pts = probe(master,'-select_streams','v:0','-show_entries','frame=best_effort_timestamp_time')['frames']
        checks = {
            'video': (v['codec_name'],v['width'],v['height'],v['pix_fmt']) == ('h264',1080,1920,'yuv420p'),
            'numero_frame': int(v['nb_read_frames']) == frames and len(pts)==frames,
            'timestamp_cfr': all(abs(float(f['best_effort_timestamp_time'])-i/FPS)<0.00001 for i,f in enumerate(pts)),
            'audio': au['codec_name']=='aac' and au['sample_rate']=='48000',
            'durata_audio': abs(float(au['duration'])-float(seconds))<=1/FPS}
        analysis = run(base+['-i',master,'-vf','blackdetect=d=0.032:pix_th=0.04',
                             '-af',loud+':print_format=json','-f','null','-'],capture_output=True).stderr.decode()
        levels = json.JSONDecoder().raw_decode('{'+analysis.rsplit('{',1)[1])[0]
        checks.update(true_peak=float(levels['input_tp'])<=-1,
                      loudness=-17<=float(levels['input_i'])<=-14,
                      nessun_frame_nero='black_start:' not in analysis)
        if not all(checks.values()):
            raise ValueError("QA bloccante: "+json.dumps(checks))
        run(base+['-loglevel','error','-i',master,'-frames:v','1','-threads','1','cover.png'],cwd=folder)
        shutil.copy(srt,folder/'captions.srt')
        report = dict(host=os.uname().sysname if hasattr(os,'uname') else os.name,frames=frames,checks=checks,
                      audio_measurements=levels,elapsed_seconds=round(time.monotonic()-start,3),
                      renderer_resolution=[1080,1920],master_bytes=master.stat().st_size,
                      input_sha256={k:digest(path) for k,path in [('clip',clip),('voice',voice),('srt',srt),('renderer',renderer)]},
                      master_sha256=digest(master),keyword=a.keyword,revisione_umana='NON ESEGUITO')
        (folder/'qa.json').write_text(json.dumps(report,indent=2)+'\n')
        result=folder/'result'
        result.mkdir()
        for name in ['master.mp4','cover.png','captions.srt','qa.json']:
            (folder/name).rename(result/name)
        result.rename(out)
    print(out/'qa.json')

if __name__ == '__main__':
    main()
