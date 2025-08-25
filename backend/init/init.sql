CREATE EXTENSION
IF NOT EXISTS postgis;
CREATE EXTENSION
IF NOT EXISTS postgis_topology;

CREATE DATABASE vechai_db_test;

\connect vechai_db_test;
CREATE EXTENSION IF NOT EXISTS postgis;