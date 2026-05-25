-- Kochvaia initial schema.
-- All ids are 26-char Crockford base32. Timestamps are unix milliseconds.

CREATE TABLE families (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  tz          TEXT NOT NULL DEFAULT 'UTC',     -- IANA timezone
  created_at  INTEGER NOT NULL
);

CREATE TABLE parents (
  id              TEXT PRIMARY KEY,
  family_id       TEXT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  google_sub      TEXT NOT NULL UNIQUE,
  email           TEXT,
  display_name    TEXT,
  created_at      INTEGER NOT NULL
);
CREATE INDEX idx_parents_family ON parents(family_id);

CREATE TABLE kids (
  id              TEXT PRIMARY KEY,
  family_id       TEXT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  display_name    TEXT NOT NULL,
  avatar_emoji    TEXT NOT NULL DEFAULT '⭐',
  avatar_color    TEXT NOT NULL DEFAULT '#FFB347',
  archived_at     INTEGER,
  created_at      INTEGER NOT NULL
);
CREATE INDEX idx_kids_family ON kids(family_id) WHERE archived_at IS NULL;

-- One row per star earned. used_at IS NULL -> available, else consumed.
CREATE TABLE stars (
  id                      TEXT PRIMARY KEY,
  kid_id                  TEXT NOT NULL REFERENCES kids(id) ON DELETE CASCADE,
  earned_date             TEXT NOT NULL,                    -- 'YYYY-MM-DD' in family tz
  awarded_at              INTEGER NOT NULL,
  awarded_by_parent_id    TEXT REFERENCES parents(id) ON DELETE SET NULL,
  used_at                 INTEGER,
  used_in_batch_id        TEXT
);
-- enforces "at most one star per kid per day"
CREATE UNIQUE INDEX uniq_stars_kid_day ON stars(kid_id, earned_date);
CREATE INDEX idx_stars_kid_awarded ON stars(kid_id, awarded_at);
CREATE INDEX idx_stars_kid_unused ON stars(kid_id, awarded_at) WHERE used_at IS NULL;
CREATE INDEX idx_stars_kid_used_at ON stars(kid_id, used_at) WHERE used_at IS NOT NULL;

CREATE TABLE deductions (
  id                          TEXT PRIMARY KEY,
  kid_id                      TEXT NOT NULL REFERENCES kids(id) ON DELETE CASCADE,
  count                       INTEGER NOT NULL CHECK (count > 0),
  reason                      TEXT,
  performed_at                INTEGER NOT NULL,
  performed_by_parent_id      TEXT REFERENCES parents(id) ON DELETE SET NULL
);
CREATE INDEX idx_deductions_kid ON deductions(kid_id, performed_at);

CREATE TABLE parent_sessions (
  token_hash      TEXT PRIMARY KEY,
  parent_id       TEXT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
  created_at      INTEGER NOT NULL,
  last_seen_at    INTEGER NOT NULL,
  revoked_at      INTEGER
);
CREATE INDEX idx_parent_sessions_parent ON parent_sessions(parent_id);

CREATE TABLE kid_sessions (
  token_hash      TEXT PRIMARY KEY,
  kid_id          TEXT NOT NULL REFERENCES kids(id) ON DELETE CASCADE,
  device_label    TEXT,
  created_at      INTEGER NOT NULL,
  last_seen_at    INTEGER NOT NULL,
  revoked_at      INTEGER
);
CREATE INDEX idx_kid_sessions_kid ON kid_sessions(kid_id);

-- Short-lived QR pairing codes. kind = 'parent' (co-parent invite) or 'kid' (pair kid device).
CREATE TABLE join_codes (
  code                    TEXT PRIMARY KEY,
  family_id               TEXT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  kind                    TEXT NOT NULL CHECK (kind IN ('parent','kid')),
  kid_id                  TEXT REFERENCES kids(id) ON DELETE CASCADE,
  created_by_parent_id    TEXT NOT NULL REFERENCES parents(id) ON DELETE CASCADE,
  created_at              INTEGER NOT NULL,
  expires_at              INTEGER NOT NULL,
  consumed_at             INTEGER
);
CREATE INDEX idx_join_codes_family ON join_codes(family_id);

-- "What was the newest star/deduction this device had seen on this kid's profile."
-- Drives the kid-profile arrival animation.
--
-- Uses a (timestamp, id) high-water-mark so multiple events in the same
-- millisecond are not double-counted or lost. Defaults of (0, '') mean
-- "nothing seen yet" — though in practice we always seed on first visit.
CREATE TABLE view_checkpoints (
  session_token_hash          TEXT NOT NULL,
  viewed_kid_id               TEXT NOT NULL REFERENCES kids(id) ON DELETE CASCADE,
  last_seen_star_awarded_at   INTEGER NOT NULL DEFAULT 0,
  last_seen_star_id           TEXT    NOT NULL DEFAULT '',
  last_seen_deduction_at      INTEGER NOT NULL DEFAULT 0,
  last_seen_deduction_id      TEXT    NOT NULL DEFAULT '',
  PRIMARY KEY (session_token_hash, viewed_kid_id)
);
