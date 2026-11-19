"""Verifica interoperabilita ZIP PowerShell/JVM e ingresso del renderer Windows."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pwsh', required=True, type=Path)
    parser.add_argument('--jdk', required=True, type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='studio-zip-') as directory:
        temp = Path(directory)
        install = temp / 'Applicazione Studio'
        install.mkdir()
        for name in ('Export-M0.ps1', 'PortableProject.ps1', 'Quality.ps1', 'LocalAi.ps1'):
            shutil.copyfile(ROOT / 'windows' / name, install / name)
        shutil.copytree(ROOT / 'brand', install / 'fonts')
        run('cc', '-O2', ROOT/'core/scene.c', ROOT/'core/render.c', '-lm',
            '-o', install/'scene-frames.exe')
        clip = temp/'clip.mp4'
        run('ffmpeg', '-nostdin', '-v', 'error', '-f', 'lavfi', '-i',
            'testsrc2=s=360x640:r=30:d=5', '-c:v', 'libx264', '-threads', '2', clip)
        voice = ROOT/'tests/asr/voce.wav'
        srt = ROOT/'qa/asr/accettata/result.srt'
        config = temp/'runtime.json'
        config.write_text(json.dumps(dict(Ffmpeg=shutil.which('ffmpeg'), Ffprobe=shutil.which('ffprobe'))))
        win_zip, java_zip = temp/'windows.zip', temp/'android.zip'
        common = (args.pwsh, '-NoProfile', '-File', install/'Export-M0.ps1', '-RuntimeConfig', config)
        run(*common, '-Clip', clip, '-Voce', voice, '-Sottotitoli', srt,
            '-Destinazione', temp/'windows.mp4', '-SalvaProgetto', win_zip)
        tampered = temp/'tampered.zip'
        with ZipFile(win_zip) as source, ZipFile(tampered, 'x') as target:
            for name in source.namelist():
                data = source.read(name)
                target.writestr(name, data + b'\n' if name == 'captions.srt' else data)
        traversal = temp/'traversal.zip'
        shutil.copyfile(win_zip, traversal)
        with ZipFile(traversal, 'a') as archive:
            archive.writestr('../escaped.txt', 'Rifiutare questa voce.')
        source = ROOT/'android/src/it/michetti/studio'
        classes = temp/'classes'
        run(args.jdk/'bin/javac', '-encoding', 'UTF-8', '-d', classes,
            *(source/name for name in ('CaptionCue.java', 'CaptionSrt.java', 'PortableProject.java')),
            ROOT/'tests/android-project/PortableProjectTest.java')
        run(args.jdk/'bin/java', '-cp', classes, 'it.michetti.studio.PortableProjectTest',
            clip, voice, srt, win_zip, java_zip, tampered, traversal)
        master = temp/'android-archive.mp4'
        run(*common, '-Progetto', java_zip, '-Destinazione', master)
        report = json.loads(next(temp.glob('android-archive.mp4.*/qa.json')).read_text())
        for field, file in [('input_bundle_sha256', java_zip), ('clip_sha256', clip),
                            ('voice_sha256', voice), ('master_sha256', master)]:
            assert report[field] == sha(file), field
        for archive, reason in ((tampered, 'alterato'), (traversal, 'sconosciuta')):
            failed = subprocess.run([str(arg) for arg in (*common, '-Progetto', archive,
                                                         '-Destinazione', temp/'rejected.mp4')],
                                    text=True, capture_output=True)
            assert failed.returncode and reason in failed.stderr
            assert not (temp/'rejected.mp4').exists()
        print('PASS PowerShell renderizza lo ZIP JVM, registra gli hash e blocca hash alterati e percorsi estranei.')
        print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
