-- Rewards catalog: parent-managed items each costing a number of stars.
-- Visible to all family sessions (parents AND kids); mutations are
-- parent-only (enforced in the route layer).

CREATE TABLE items (
  id              TEXT PRIMARY KEY,
  family_id       TEXT NOT NULL REFERENCES families(id) ON DELETE CASCADE,
  name            TEXT NOT NULL,
  cost_stars      INTEGER NOT NULL CHECK (cost_stars > 0),
  emoji           TEXT NOT NULL DEFAULT '🎁',
  archived_at     INTEGER,
  created_at      INTEGER NOT NULL
);

-- Partial index: most queries only want active items in a family.
CREATE INDEX idx_items_family_active
  ON items(family_id, created_at)
  WHERE archived_at IS NULL;
