import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from zipfile import ZipFile

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / 'data'))
import compare
import studio_project as store


def encode_fixture(folder, color, tone):
    subprocess.run(['ffmpeg','-nostdin','-v','error','-f','lavfi','-i',
                    f'color=c={color}:size=1080x1920:rate=30:duration=2','-f','lavfi','-i',
                    f'sine=frequency={tone}:sample_rate=48000:duration=2','-af','volume=6.5dB',
                    '-c:v','libx264','-preset','ultrafast','-threads','2','-pix_fmt','yuv420p',
                    '-c:a','aac','-b:a','160k',str(folder / 'master.mp4')],check=True)


class Compare(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.t = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.t.cleanup)
        root = Path(cls.t.name)
        project = root / 'p'
        store.create(project, 'Fixture di parità')
        cls.bundle = root / 'b.zip'
        store.backup(project, cls.bundle)
        dh = hashlib.sha256(cls.bundle.read_bytes()).hexdigest()
        cls.a, cls.b = root / 'a', root / 'b'
        for folder in (cls.a, cls.b):
            folder.mkdir()
            with ZipFile(cls.bundle) as z:
                (folder / 'project_manifest.json').write_bytes(z.read('project_manifest.json'))
            ledger = dict(bundle_sha256=dh, seed=123, frames=60, scenes=[
                dict(id='s1', start_frame=0, end_frame=60, motion=dict(type='fixture', angle=23))
            ])
            (folder / 'render.json').write_text(json.dumps(ledger))
            (folder / 'captions.srt').write_text('1\n00:00:00,100 --> 00:00:01,450\nProva locale\n')
        encode_fixture(cls.a, '0x0A1628', 440)
        encode_fixture(cls.b, '0xE6C777', 900)

    def test_identical(self):
        result = compare.compare(self.a, self.a, self.bundle)
        self.assertEqual(result['esito'], 'PASS', result)
        self.assertEqual(result['errore_rgb_medio_massimo'], 0)
        self.assertAlmostEqual(result['correlazione_audio'], 1)

    def test_different_media(self):
        result = compare.compare(self.a, self.b, self.bundle)
        self.assertEqual(result['esito'], 'FAIL')
        self.assertFalse(result['controlli']['frame_rgb'])
        self.assertFalse(result['controlli']['tile_rgb'])
        self.assertFalse(result['controlli']['waveform'])

    def test_unbound_plan(self):
        with self.assertRaisesRegex(ValueError, 'bundle'):
            compare.inspect_ledger(self.a, '0' * 64, {})

    def test_invalid_cue(self):
        path = Path(self.t.name) / 'bad.srt'
        path.write_text('1\n00:00:00,100 --> 00:01:00,000\nX')
        with self.assertRaisesRegex(ValueError, 'fuori timeline'):
            compare.cues(path, 60)
