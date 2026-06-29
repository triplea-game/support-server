-- Tracks the outcome of each map-indexing pass (one row per repo, latest result only), and widens
-- map_index.disable_reason so it can hold a multi-line indexing error message.

create table map_indexing_status
(
    id                    serial primary key,
    repo_url              varchar(256) not null unique check (repo_url like 'http%'),
    repo_name             varchar(256) not null,
    -- Timestamp of the most recent indexing attempt, regardless of outcome.
    last_indexing_attempt timestamptz  not null default now(),
    -- Timestamp of the most recent successful index; null until the repo has indexed at least once.
    last_success          timestamptz,
    -- The IndexingResult.ResultCode name: SUCCESSFULLY_INDEXED, INDEXING_IS_UP_TO_DATE, REPO_ERROR.
    result_code           varchar(32)  not null,
    -- Joined indexing error message(s); null unless result_code is an error.
    error_message         varchar(4000),
    date_created          timestamptz  not null default now(),
    date_updated          timestamptz  not null default now()
);

comment on table map_indexing_status is
    'Latest indexing outcome per map repo: when last attempted/succeeded, the result, and any error.';

-- Indexing errors are stored in map_index.disable_reason; the indexer''s messages are multi-line
-- and longer than the original 1024 cap.
alter table map_index
    alter column disable_reason type varchar(4000);
