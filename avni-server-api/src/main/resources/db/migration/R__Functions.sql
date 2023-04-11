DROP FUNCTION IF EXISTS create_db_user(inrolname text, inpassword text);
CREATE OR REPLACE FUNCTION create_db_user(inrolname text, inpassword text)
  RETURNS BIGINT AS
$BODY$
  BEGIN
    IF NOT EXISTS(SELECT rolname FROM pg_roles WHERE rolname = inrolname)
    THEN
      EXECUTE 'CREATE ROLE ' || quote_ident(inrolname) || ' NOINHERIT LOGIN PASSWORD ' || quote_literal(inpassword);
    END IF;
    EXECUTE 'GRANT ' || quote_ident(inrolname) || ' TO openchs';
    PERFORM grant_all_on_all(inrolname);
    RETURN 1;
  END
$BODY$ LANGUAGE PLPGSQL;


DROP FUNCTION IF EXISTS create_implementation_schema(text);
CREATE OR REPLACE FUNCTION create_implementation_schema(schema_name text, db_user text)
  RETURNS BIGINT AS
$BODY$
BEGIN
  EXECUTE 'CREATE SCHEMA IF NOT EXISTS "' || schema_name || '" AUTHORIZATION "' || db_user || '"';
  EXECUTE 'GRANT ALL PRIVILEGES ON SCHEMA "' || schema_name || '" TO "' || db_user || '"';
  RETURN 1;
END
$BODY$ LANGUAGE PLPGSQL;


CREATE OR REPLACE FUNCTION jsonb_object_values_contain(obs JSONB, pattern TEXT)
  RETURNS BOOLEAN AS $$
BEGIN
  return EXISTS (select true from jsonb_each_text(obs) where value ilike pattern);
END;
$$
LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION create_audit(user_id NUMERIC)
  RETURNS INTEGER AS $$
DECLARE result INTEGER;
BEGIN
  INSERT INTO audit(created_by_id, last_modified_by_id, created_date_time, last_modified_date_time)
  VALUES(user_id, user_id, now(), now()) RETURNING id into result;
  RETURN result;
END $$
  LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_audit()
  RETURNS INTEGER AS 'select create_audit(1)' language sql;

-- These were failing tests, removing them for now.
-- DROP function if exists get_observation_pattern;
-- DROP function if exists get_outer_query(text, text);
-- DROP function if exists get_outer_query(text);
-- DROP function if exists web_search_function;

DROP FUNCTION IF EXISTS create_view(text, text, text);
CREATE OR REPLACE FUNCTION create_view(schema_name text, view_name text, sql_query text)
    RETURNS BIGINT AS
$BODY$
BEGIN
--     EXECUTE 'set search_path = ' || ;
    EXECUTE 'DROP VIEW IF EXISTS ' || schema_name || '.' || view_name;
    EXECUTE 'CREATE OR REPLACE VIEW ' || schema_name || '.' || view_name || ' AS ' || sql_query;
    RETURN 1;
END
$BODY$ LANGUAGE PLPGSQL;

DROP FUNCTION IF EXISTS drop_view(text, text);
CREATE OR REPLACE FUNCTION drop_view(schema_name text, view_name text)
    RETURNS BIGINT AS
$BODY$
BEGIN
    EXECUTE 'set search_path = ' || schema_name;
    EXECUTE 'DROP VIEW IF EXISTS ' || view_name;
    EXECUTE 'reset search_path';
    RETURN 1;
END
$BODY$ LANGUAGE PLPGSQL;

CREATE OR REPLACE FUNCTION concept_name(TEXT) RETURNS TEXT
    stable
    strict
    language sql
as
$$
SELECT name
FROM concept
WHERE uuid = $1;
$$;

create or replace function get_coded_string_value(obs jsonb, obs_store hstore)
    returns character varying
    language plpgsql
    SECURITY INVOKER
    stable
as
$$
DECLARE
    result VARCHAR;
BEGIN
    BEGIN
        IF JSONB_TYPEOF(obs) = 'array'
        THEN
            select STRING_AGG(obs_store -> OB.UUID, ', ') from JSONB_ARRAY_ELEMENTS_TEXT(obs) AS OB (UUID)
            INTO RESULT;
        ELSE
            SELECT obs_store -> (obs ->> 0) INTO RESULT;
        END IF;
        RETURN RESULT;
    EXCEPTION
        WHEN OTHERS
            THEN
                RAISE NOTICE 'Failed while processing get_coded_string_value(''%'')', obs :: TEXT;
                RAISE NOTICE '% %', SQLERRM, SQLSTATE;
    END;
END
$$;


create or replace function append_manual_update_history(current_value text, text_to_be_added text)
    returns text
    language plpgsql
    SECURITY INVOKER
    immutable
as
$$
BEGIN
    if current_value is null or trim(current_value) = '' then
        RETURN concat(to_char(current_timestamp, 'DD/MM/YYYY hh:mm:ss'), ' - ', text_to_be_added);
    else
        RETURN concat(to_char(current_timestamp, 'DD/MM/YYYY hh:mm:ss'), ' - ', text_to_be_added, ' || ',
                      current_value);
    end if;
END
$$;

-- Create or replace function to delete etl metadata for an org
create or replace function delete_etl_metadata_for_schema(in_impl_schema text, in_db_user text) returns bool
    language plpgsql
as
$$
BEGIN
    EXECUTE 'set role ' || in_impl_schema || ';';
    execute 'drop schema ' || in_impl_schema || ' cascade;';
    execute 'delete from entity_sync_status where db_user = ''' || in_db_user || ''';';
    execute 'delete from entity_sync_status where schema_name = ''' || in_impl_schema || ''';';
    execute 'delete from index_metadata where table_metadata_id in (select id from table_metadata where schema_name = ''' || in_impl_schema || ''');';
    execute 'delete from column_metadata where table_id in (select id from table_metadata where schema_name = ''' || in_impl_schema || ''');';
    execute 'delete from table_metadata where schema_name = ''' || in_impl_schema || ''';';
    return true;
END
$$;

-- Create or replace function to delete etl metadata for org-group
create or replace function delete_etl_metadata_for_org(in_impl_schema text, in_db_user text) returns bool
    language plpgsql
as
$$
BEGIN
    EXECUTE 'set role openchs;';
    execute 'drop schema ' || in_impl_schema || ' cascade;';
    execute 'delete from entity_sync_status where db_user = ''' || in_db_user || ''';';
    execute 'delete from entity_sync_status where schema_name = ''' || in_impl_schema || ''';';
    execute 'delete from index_metadata where table_metadata_id in (select id from table_metadata where schema_name = ''' || in_impl_schema || ''');';
    execute 'delete from column_metadata where table_id in (select id from table_metadata where schema_name = ''' || in_impl_schema || ''');';
    execute 'delete from table_metadata where schema_name = ''' || in_impl_schema || ''';';
    return true;
END
$$;