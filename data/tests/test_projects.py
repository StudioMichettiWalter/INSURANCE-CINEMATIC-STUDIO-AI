import copy
import json
from pathlib import Path
import sqlite3
import stat
import sys
import tempfile
import unittest
import warnings
from jsonschema.exceptions import ValidationError
from zipfile import ZipFile, ZipInfo

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import studio_project as s


class Projects(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.folder = Path(self.tmp.name)
        self.root = self.folder / 'progetto'
        s.create(self.root, 'Verifica locale')
        self.clip = self.folder / 'clip.fixture'
        self.clip.write_bytes(b'fixture per lo storage, nessuna codifica media')
        self.bundle = self.folder / 'backup.zip'

    def test_schema_and_template(self):
        s.Draft202012Validator.check_schema(s.SCHEMA.schema)
        s.validate(json.loads((s.BASE / 'project_manifest.json').read_text()))
        manifest = s.current(self.root)
        manifest['status'] = 'inventato'
        with self.assertRaises(ValidationError):
            s.commit(self.root, manifest)
        self.assertEqual(s.current(self.root)['revision'], 1)

    def test_history_backup_restore(self):
        asset = s.ingest(self.root, self.clip)
        self.assertEqual(asset, s.ingest(self.root, self.clip))
        draft = s.current(self.root)
        draft['assets'] = []
        draft['script'] = 'Trascrizione approvata'
        s.commit(self.root, draft)
        s.backup(self.root, self.bundle)
        recovered = self.folder / 'ripristinato'
        s.restore(self.bundle, recovered)
        self.assertEqual(s.current(self.root), s.current(recovered))
        self.assertEqual((recovered / asset['path']).read_bytes(), self.clip.read_bytes())
        for root in (self.root, recovered):
            with s.open_db(root) as db:
                self.assertEqual(db.execute('select count(*) from revisions').fetchone(), (3,))
                with self.assertRaises(sqlite3.IntegrityError):
                    db.execute('delete from revisions')
        with self.assertRaises(FileExistsError):
            s.restore(self.bundle, self.root)
        with self.assertRaises(FileExistsError):
            s.backup(self.root, self.bundle)

    def test_stale_edit(self):
        draft = s.current(self.root)
        stale = copy.deepcopy(draft)
        draft['script'] = 'Salvato'
        s.commit(self.root, draft)
        with self.assertRaisesRegex(ValueError, 'Conflitto'):
            s.commit(self.root, stale)
        self.assertEqual(s.current(self.root)['script'], 'Salvato')

    def test_digest_source(self):
        a = s.ingest(self.root, self.clip)
        (self.root / a['path']).write_bytes(b'X')
        with self.assertRaisesRegex(ValueError, 'corrotto'):
            s.backup(self.root, self.bundle)
        self.assertFalse(self.bundle.exists())

    def test_unknown_db_version(self):
        with s.open_db(self.root) as db:
            db.execute('pragma user_version=3')
        with self.assertRaisesRegex(ValueError, 'Versione DB'):
            s.current(self.root)

    def test_reject_duplicate_keys(self):
        with self.assertRaisesRegex(ValueError, 'duplicata'):
            s.parse(b'{"a":1,"a":2}')

    def test_untrusted_zip(self):
        s.ingest(self.root, self.clip)
        s.backup(self.root, self.bundle)
        asset = s.current(self.root)['assets'][0]['path']
        cases = ['escape', 'symlink', 'corrupt', 'duplicate', 'latest', 'orphan', 'missing']
        for case in cases:
            with self.subTest(case=case):
                bad = self.folder / (case + '.zip')
                with ZipFile(self.bundle) as src, ZipFile(bad, 'w') as dst:
                    for name in src.namelist():
                        if case == 'missing' and name == asset:
                            continue
                        payload = src.read(name)
                        if case == 'corrupt' and name == asset:
                            payload = bytes(len(payload))
                        if case == 'latest' and name == 'project_manifest.json':
                            payload = src.read('revisions/00000001.json')
                        dst.writestr(name, payload)
                    if case == 'escape':
                        dst.writestr('../uscita', 'x')
                    if case == 'symlink':
                        i = ZipInfo('assets/' + '0' * 64)
                        i.external_attr = (stat.S_IFLNK | 0o777) << 16
                        dst.writestr(i, '../../uscita')
                    if case == 'orphan':
                        dst.writestr('assets/' + '0' * 64, b'x')
                    if case == 'duplicate':
                        with warnings.catch_warnings():
                            warnings.simplefilter('ignore', UserWarning)
                            dst.writestr(asset, src.read(asset))
                dest = self.folder / 'recovered'
                with self.assertRaises(ValueError):
                    s.restore(bad, dest)
                self.assertFalse(dest.exists())
                self.assertFalse((self.folder / 'uscita').exists())

    def test_no_arbitrary_path(self):
        s.ingest(self.root, self.clip)
        draft = s.current(self.root)
        draft['assets'][0]['path'] = 'assets/' + '0' * 64
        with self.assertRaisesRegex(ValueError, 'Percorso'):
            s.commit(self.root, draft)
        self.assertEqual(s.current(self.root)['revision'], 2)


if __name__ == '__main__':
    unittest.main()
