-- Adds a MapAdmin approval flag to map_index, independent of the indexer-owned `enabled` flag.
-- A map is only publicly listed when it is BOTH indexer-enabled AND admin-approved. The two axes
-- never interact: `enabled`/`disable_reason` are owned by the indexing job (can we read the map?),
-- `admin_enabled`/`admin_disable_reason` are owned by MapAdmins (do we allow the map?).
--
-- The column shape mirrors `enabled`/`disable_reason`: the *default* state is the no-reason side
-- (approved, reason null), and the other state must supply a reason. Defaulting to approved
-- grandfathers every map that already exists at migration time, so the change is non-disruptive.
--
-- New maps must instead start unapproved ("pending approval"): the indexer's inserts set
-- `admin_enabled = false` with that reason explicitly (see MapIndexDao), and its ON CONFLICT
-- updates never name these columns, so re-indexing leaves a MapAdmin's decision untouched.
alter table map_index
    add column admin_enabled boolean not null default true,
    add column admin_disable_reason varchar(1024),
    -- When approved, there must be no reason; when not approved, a reason is required.
    add constraint map_index_admin_disable_reason_ck
        check ((admin_enabled and admin_disable_reason is null)
            or (not admin_enabled and admin_disable_reason is not null));
