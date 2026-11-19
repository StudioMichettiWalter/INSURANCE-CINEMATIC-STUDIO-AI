"""Confronto diagnostico delle esportazioni M0.3 generate con il kit sintetico."""
import argparse
from hashlib import file_digest
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from zipfile import BadZipFile, ZipFile

import compare

VIDEO_LIMIT = 32 * 1024**2
TEXT_LIMIT = 1024**2


def digest(path):
    with path.open('rb') as stream:
        return file_digest(stream, 'sha256').hexdigest()


def extract(archive, directory, names):
    """Estrae esclusivamente i file attesi, in una directory nuova."""
    directory.mkdir()
    with ZipFile(archive) as bundle:
        for name in names:
            info = bundle.getinfo(name)
            limit = VIDEO_LIMIT if name == 'master.mp4' else TEXT_LIMIT
            if not 0 < info.file_size <= limit or bundle.namelist().count(name) != 1:
                raise ValueError(f'File ZIP duplicato, vuoto o troppo grande: {name}')
            with bundle.open(info) as source, (directory / name).open('xb') as target:
                shutil.copyfileobj(source, target)


def json_file(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def check_provenance(report, folder, fixture):
    expected = fixture['files']
    for field, name in [('clip_sha256', 'clip.mp4'), ('voice_sha256', 'voce.wav')]:
        if report[field] != expected[name]:
            raise ValueError(f'{name} usato nel render diverso dal kit')
    if report['master_sha256'] != digest(folder / 'master.mp4'):
        raise ValueError('Il master archiviato non coincide con il rapporto')


def assess(windows, android, kit):
    with tempfile.TemporaryDirectory(prefix='studio-confronto-') as directory:
        temp = Path(directory)
        left, right, reference = temp / 'win', temp / 'android', temp / 'kit'
        names = ['master.mp4', 'captions.srt', 'qa.json']
        extract(windows, left, names + ['prova.json'])
        extract(android, right, names)
        extract(kit, reference, ['fixture.json', 'riferimento.srt'])

        fixture = json_file(reference / 'fixture.json')
        frames = fixture['frames']
        if fixture['id'] != 'm0.3-media-italiano' or frames != 99:
            raise ValueError('Fixture M0.3 attesa')
        if digest(reference / 'riferimento.srt') != fixture['files']['riferimento.srt']:
            raise ValueError('SRT di riferimento alterato')
        win = json_file(left / 'prova.json')
        android_report = json_file(right / 'qa.json')
        if win['fixture'] != fixture['id'] or win['mode'] != 'srt':
            raise ValueError('Per il confronto usare lo ZIP Windows creato senza -Automatico')
        if android_report['platform'] != 'Android' or android_report['app_version'] != '0.1.0-m0.3':
            raise ValueError('Per il confronto usare lo ZIP generato dall’app Android M0.3')
        if android_report['captions_source'] != 'SRT importato manualmente':
            raise ValueError('Su Android importare riferimento.srt prima del render')
        if json_file(left / 'qa.json')['status'] != 'QA' or android_report['status'] != 'QA':
            raise ValueError('Una verifica automatica è rimasta aperta o ha bloccato l’export')
        check_provenance(win, left, fixture)
        check_provenance(android_report, right, fixture)

        expected_cues = compare.cues(reference / 'riferimento.srt', frames)
        indices = [i * (frames - 1) // 8 for i in range(9)]
        result = compare.compare_media(left / 'master.mp4', right / 'master.mp4',
                                       frames, frames, indices)
        checks = result['controlli']
        for platform, folder in [('windows', left), ('android', right)]:
            checks[f'cue_{platform}_uguali_al_riferimento'] = (
                compare.cues(folder / 'captions.srt', frames) == expected_cues)
        result.update(esito_tecnico='PASS' if all(checks.values()) else 'FAIL',
                      accettazione_m0='PENDENTE', approvazione_soglie='PENDENTE',
                      fixture=fixture['id'], windows_os=win['os'],
                      android_device=android_report.get('device'))
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('windows', 'android', 'kit'):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    try:
        result = assess(args.windows, args.android, args.kit)
    except (OSError, ValueError, KeyError, TypeError, IndexError, BadZipFile,
            RuntimeError, subprocess.TimeoutExpired) as error:
        result = dict(esito_tecnico='FAIL', accettazione_m0='PENDENTE', errore=str(error))
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0 if result['esito_tecnico'] == 'PASS' else 1


if __name__ == '__main__':
    sys.exit(main())
