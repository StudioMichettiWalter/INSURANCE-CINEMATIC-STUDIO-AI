CREATE TABLE revisions (
    revision INTEGER PRIMARY KEY CHECK (revision >= 1),
    digest TEXT NOT NULL UNIQUE,
    manifest BLOB NOT NULL
);
CREATE TRIGGER immutable_update BEFORE UPDATE ON revisions
BEGIN SELECT RAISE(ABORT,'revisioni immutabili'); END;
CREATE TRIGGER immutable_delete BEFORE DELETE ON revisions
BEGIN SELECT RAISE(ABORT,'revisioni immutabili'); END;
PRAGMA user_version = 1;
