-- Adds an enable/disable flag to map_index, with a reason message required when disabled.

alter table map_index
    add column enabled boolean not null default true,
    add column disable_reason varchar(1024),
    -- When enabled, there must be no disable reason; when disabled, a reason is required.
    add constraint map_index_disable_reason_ck
        check ((enabled and disable_reason is null)
            or (not enabled and disable_reason is not null));
