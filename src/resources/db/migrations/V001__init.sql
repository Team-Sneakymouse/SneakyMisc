-- plugin_version_history: startup snapshots from Paper servers and Velocity proxies.
-- Adopt on Live by marking V001 applied when the table already exists (runtime DDL before migrations).

CREATE TABLE plugin_version_history (
    server_id text NOT NULL,
    server_name text NOT NULL,
    started_at timestamptz NOT NULL,
    platform text NOT NULL,
    platform_version text NOT NULL,
    minecraft_version text,
    kind text NOT NULL,
    name text NOT NULL,
    version text,
    jar text,
    enabled boolean,
    PRIMARY KEY (server_id, started_at, kind, name)
);