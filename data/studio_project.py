"""Strumento host per progetti e backup M0. Tutti i percorsi ricevuti dal bundle sono dati."""
import argparse
from contextlib import closing, contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import stat
import tempfile
import uuid
import zipfile

from jsonschema import Draft202012Validator

BASE = Path(__file__).resolve().parent
SCHEMA = Draft202012Validator(json.loads((BASE / 'manifest.schema.json').read_text()))
MAX_MANIFEST = 2 * 1024**2
MAX_TOTAL = 8 * 1024**3
MAX_ENTRIES = 8192
CHUNK = 1024 * 1024


def unique_keys(pairs):
    obj = {}
    for key, value in pairs:
        if key in obj:
            raise ValueError('Chiave JSON duplicata')
        obj[key] = value
    return obj


def parse(raw):
    if len(raw) > MAX_MANIFEST:
        raise ValueError('Manifest troppo grande')
    return json.loads(raw.decode('utf-8'), object_pairs_hook=unique_keys)


def encode(obj):
    return json.dumps(obj, ensure_ascii=False, separators=(',', ':'), sort_keys=True,
                      allow_nan=False).encode('utf-8')


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def validate(m):
    SCHEMA.validate(m)
    if len(encode(m)) > MAX_MANIFEST:
        raise ValueError('Manifest troppo grande')
    for collection in ['sources', 'assets', 'storyboard']:
        ids = [row['id'] for row in m[collection]]
        if len(set(ids)) != len(ids):
            raise ValueError('ID duplicato in ' + collection)
    assets = {a['id'] for a in m['assets']}
    scenes = {s['id'] for s in m['storyboard']}
    for a in m['assets']:
        if a['path'] != 'assets/' + a['sha256']:
            raise ValueError('Percorso non conforme al digest')
    for s in m['storyboard']:
        if s['end_frame'] <= s['start_frame'] or set(s['asset_ids']) - assets:
            raise ValueError('Scena con intervallo o riferimento errato')
    if any(p['scene_id'] not in scenes for p in m['prompts']):
        raise ValueError('Prompt senza scena')
    if (m['revision'] == 1) != (m['parent_sha256'] is None):
        raise ValueError('Parent non valido')


def copy_hash(src, dst=None, limit=MAX_TOTAL):
    h = hashlib.sha256()
    size = 0
    while data := src.read(CHUNK):
        size += len(data)
        if size > limit:
            raise ValueError('Limite dimensione superato')
        if dst is not None:
            dst.write(data)
        h.update(data)
    return h.hexdigest(), size


def empty(title):
    return dict(schema_version=1, project_id=str(uuid.uuid4()), title=title, revision=1,
                parent_sha256=None, status='PREPRODUCTION', fps=30, locale='it-IT',
                brand=dict(navy='#0A1628', gold='#E6C777', ivory='#F6F1E3'),
                assets=[], script='', storyboard=[], prompts=[], sources=[])


@contextmanager
def stage_dir(destination):
    destination = Path(destination)
    if destination.exists() or destination.is_symlink():
        raise FileExistsError(destination)
    temp = Path(tempfile.mkdtemp(prefix='.studio-', dir=destination.parent))
    try:
        (temp / 'assets').mkdir()
        with closing(sqlite3.connect(temp / 'project.sqlite')) as db:
            schema = (BASE / 'schema.sql').read_text()
            db.executescript('BEGIN IMMEDIATE;\n' + schema + '\nCOMMIT;')
        yield temp
        os.rename(temp, destination)
    finally:
        if temp.exists():
            shutil.rmtree(temp)


@contextmanager
def open_db(root):
    # mode=rw impedisce la creazione di DB vuoti per un percorso errato.
    uri = (Path(root) / 'project.sqlite').resolve().as_uri() + '?mode=rw'
    with closing(sqlite3.connect(uri, uri=True, timeout=10)) as db:
        if db.execute('pragma user_version').fetchone() != (1,):
            raise ValueError('Versione DB sconosciuta')
        yield db


def record(db, m):
    raw = encode(m)
    db.execute('insert into revisions values (?,?,?)', (m['revision'], sha(raw), raw))


def head(db):
    return db.execute('select digest, manifest from revisions order by revision desc limit 1').fetchone()


def check_assets(root, m):
    for a in m['assets']:
        with (Path(root) / a['path']).open('rb') as f:
            if copy_hash(f, limit=a['bytes']) != (a['sha256'], a['bytes']):
                raise ValueError('Integrità asset compromessa')


def create(root, title):
    m = empty(title)
    validate(m)
    with stage_dir(root) as staged, open_db(staged) as db, db:
        record(db, m)


def current(root):
    with open_db(root) as db:
        digest, raw = head(db)
        if sha(raw) != digest:
            raise ValueError('Integrità revisione compromessa')
        return parse(raw)


def commit(root, draft):
    """Confronto ottimistico sul manifest di partenza prima dell'append."""
    m = draft.copy()
    validate(m)
    check_assets(root, m)
    with open_db(root) as db, db:
        db.execute('BEGIN IMMEDIATE')
        digest, raw = head(db)
        old = parse(raw)
        if sha(raw) != digest:
            raise ValueError('Integrità revisione compromessa')
        for key in ['project_id', 'revision', 'parent_sha256']:
            if m[key] != old[key]:
                raise ValueError('Conflitto di revisione: ricaricare il manifest')
        m.update(revision=old['revision'] + 1, parent_sha256=digest)
        validate(m)
        record(db, m)
    return m


def ingest(root, source):
    root = Path(root)
    source = Path(source)
    m = current(root)
    with tempfile.NamedTemporaryFile(dir=root / 'assets', delete=False) as tmp:
        name = Path(tmp.name)
    try:
        with source.open('rb') as src, name.open('wb') as dst:
            digest, size = copy_hash(src, dst)
            dst.flush()
            os.fsync(dst.fileno())
        dest = root / 'assets' / digest
        try:
            os.link(name, dest)
        except FileExistsError:
            with dest.open('rb') as existing:
                if copy_hash(existing, limit=size) != (digest, size):
                    raise ValueError('Asset esistente corrotto')
        for a in m['assets']:
            if a['sha256'] == digest:
                return a
        asset = dict(id=str(uuid.uuid4()), name=source.name, sha256=digest, bytes=size,
                     path='assets/' + digest)
        m['assets'].append(asset)
        commit(root, m)
        return asset
    finally:
        name.unlink(missing_ok=True)


def validate_chain(records):
    refs = {}
    previous = None
    project = None
    for i, raw in enumerate(records, 1):
        m = parse(raw)
        validate(m)
        if raw != encode(m) or m['revision'] != i or m['parent_sha256'] != previous:
            raise ValueError('Catena revisioni incoerente')
        if project and m['project_id'] != project:
            raise ValueError('Cambio UUID in cronologia')
        project, previous = m['project_id'], sha(raw)
        for a in m['assets']:
            if refs.setdefault(a['path'], a['bytes']) != a['bytes']:
                raise ValueError('Dimensione incoerente')
    if previous is None:
        raise ValueError('Nessuna revisione')
    return refs


def backup(root, destination):
    root, destination = Path(root), Path(destination)
    with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as f:
        tmp = Path(f.name)
    try:
        with open_db(root) as db, zipfile.ZipFile(tmp, 'w', allowZip64=True) as z:
            db.execute('BEGIN')
            def records():
                for rev, digest, raw in db.execute('select * from revisions order by revision'):
                    if sha(raw) != digest or rev != parse(raw)['revision'] or rev >= MAX_ENTRIES:
                        raise ValueError('Revisione non esportabile')
                    z.writestr(f'revisions/{rev:08d}.json', raw)
                    yield raw
            refs = validate_chain(records())
            z.writestr('project_manifest.json', head(db)[1])
            if len(z.infolist()) + len(refs) > MAX_ENTRIES or (
                sum(i.file_size for i in z.infolist()) + sum(refs.values()) > MAX_TOTAL
            ):
                raise ValueError('Dimensione o numero di elementi oltre il limite del bundle')
            for path, size in sorted(refs.items()):
                with (root / path).open('rb') as src, z.open(path, 'w', force_zip64=True) as dst:
                    if copy_hash(src, dst, size) != (path[7:], size):
                        raise ValueError('Asset corrotto durante il backup')
        os.link(tmp, destination)
    finally:
        tmp.unlink(missing_ok=True)


ENTRY = re.compile(r'project_manifest\.json|revisions/[0-9]{8}\.json|assets/[0-9a-f]{64}')


def restore(archive, root):
    with zipfile.ZipFile(archive) as z, stage_dir(root) as stage, open_db(stage) as db, db:
        infos = z.infolist()
        if len(infos) > MAX_ENTRIES or sum(i.file_size for i in infos) > MAX_TOTAL:
            raise ValueError('Limiti archivio superati')
        names = set()
        for i in infos:
            kind = stat.S_IFMT(i.external_attr >> 16)
            if (not ENTRY.fullmatch(i.orig_filename) or i.filename in names or
                    kind not in (0, stat.S_IFREG) or i.flag_bits & 1 or
                    i.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED)):
                raise ValueError('Voce archivio non consentita')
            if not i.filename.startswith('assets/') and i.file_size > MAX_MANIFEST:
                raise ValueError('Manifest troppo grande')
            names.add(i.filename)
        latest = z.read('project_manifest.json')
        def records():
            for idx, name in enumerate(sorted(k for k in names if k.startswith('revisions/')), 1):
                if name != f'revisions/{idx:08d}.json':
                    raise ValueError('Indice revisione incoerente')
                raw = z.read(name)
                yield raw
                record(db, parse(raw))
        refs = validate_chain(records())
        if latest != head(db)[1] or set(refs) != {k for k in names if k.startswith('assets/')}:
            raise ValueError('Backup incompleto o manifest corrente divergente')
        for name, size in refs.items():
            if z.getinfo(name).file_size != size:
                raise ValueError('Dimensione asset incoerente')
            with z.open(name) as src, (stage / name).open('xb') as dst:
                if copy_hash(src, dst, size) != (name[7:], size):
                    raise ValueError('Digest asset errato')


def main():
    args = argparse.ArgumentParser(description='Progetti e backup locali • strumento host M0')
    sub = args.add_subparsers(dest='action', required=True)
    for name, fields in {'new': ['project', 'title'], 'show': ['project'],
                         'commit': ['project', 'manifest'], 'ingest': ['project', 'asset'],
                         'backup': ['project', 'bundle'], 'restore': ['bundle', 'project']}.items():
        p = sub.add_parser(name)
        for field in fields:
            p.add_argument(field)
    a = args.parse_args()
    if a.action == 'new':
        create(a.project, a.title)
    elif a.action == 'show':
        print(json.dumps(current(a.project), ensure_ascii=False, indent=2))
    elif a.action == 'commit':
        with open(a.manifest, 'rb') as f:
            commit(a.project, parse(f.read(MAX_MANIFEST + 1)))
    elif a.action == 'ingest':
        print(json.dumps(ingest(a.project, a.asset), ensure_ascii=False))
    elif a.action == 'backup':
        backup(a.project, a.bundle)
    elif a.action == 'restore':
        restore(a.bundle, a.project)


if __name__ == '__main__':
    main()
