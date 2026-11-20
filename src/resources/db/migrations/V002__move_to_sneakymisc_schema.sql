-- Move plugin_version_history out of SneakyDB's default schema into SneakyMisc's own schema.
-- The operator creates schema sneakymisc and grants SneakyDB's database user USAGE and CREATE on it before
-- this script reaches a database; scripts never create schemas.

ALTER TABLE plugin_version_history SET SCHEMA sneakymisc;
