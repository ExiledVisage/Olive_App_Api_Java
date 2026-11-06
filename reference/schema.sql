--
-- PostgreSQL database dump
--

\restrict rOAscTtHB582Ryc8nyencAUUU6h1nFQUsW1DQfIi5ZcuV460aPMIlcwGMiD764i

-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: analytics_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.analytics_reports (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(120) NOT NULL,
    config json NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: analytics_reports_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.analytics_reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: analytics_reports_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.analytics_reports_id_seq OWNED BY public.analytics_reports.id;


--
-- Name: attribute_definitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attribute_definitions (
    id bigint NOT NULL,
    attribute_key character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    data_type character varying(255) NOT NULL,
    unit character varying(255),
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: attribute_definitions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.attribute_definitions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: attribute_definitions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.attribute_definitions_id_seq OWNED BY public.attribute_definitions.id;


--
-- Name: batch_process_field_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_process_field_values (
    id bigint NOT NULL,
    batch_process_id bigint NOT NULL,
    process_type_field_id bigint NOT NULL,
    value_text text,
    value_decimal numeric(15,4),
    value_integer bigint,
    value_boolean boolean,
    value_datetime timestamp(0) without time zone,
    value_json json,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: batch_process_field_values_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_process_field_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_process_field_values_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_process_field_values_id_seq OWNED BY public.batch_process_field_values.id;


--
-- Name: batch_process_reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_process_reviews (
    id bigint NOT NULL,
    batch_process_id bigint NOT NULL,
    decision character varying(20) NOT NULL,
    reviewed_by_user_id bigint,
    submitted_by_user_id bigint,
    operation_data json,
    note text,
    reviewed_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: batch_process_reviews_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_process_reviews_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_process_reviews_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_process_reviews_id_seq OWNED BY public.batch_process_reviews.id;


--
-- Name: batch_processes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.batch_processes (
    id bigint NOT NULL,
    production_batch_id bigint NOT NULL,
    production_stage_id bigint NOT NULL,
    status character varying(255) DEFAULT 'waiting'::character varying NOT NULL,
    operator_id bigint,
    started_at timestamp(0) without time zone,
    completed_at timestamp(0) without time zone,
    note text,
    operation_data json,
    submitted_by_user_id bigint,
    submitted_at timestamp(0) without time zone,
    approved_by_user_id bigint,
    approved_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: batch_processes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.batch_processes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: batch_processes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.batch_processes_id_seq OWNED BY public.batch_processes.id;


--
-- Name: cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cache (
    key character varying(255) NOT NULL,
    value text NOT NULL,
    expiration integer NOT NULL
);


--
-- Name: cache_locks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cache_locks (
    key character varying(255) NOT NULL,
    owner character varying(255) NOT NULL,
    expiration integer NOT NULL
);


--
-- Name: customer_addresses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_addresses (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    label character varying(255) NOT NULL,
    country character varying(255) DEFAULT 'TR'::character varying NOT NULL,
    city character varying(255) NOT NULL,
    district character varying(255),
    neighborhood character varying(255),
    street character varying(255),
    building_no character varying(255),
    apartment_no character varying(255),
    postal_code character varying(255),
    full_address text,
    latitude numeric(10,7),
    longitude numeric(10,7),
    is_default boolean DEFAULT false NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: customer_addresses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_addresses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_addresses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_addresses_id_seq OWNED BY public.customer_addresses.id;


--
-- Name: customer_preferred_recipes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_preferred_recipes (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    note character varying(255),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: customer_preferred_recipes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_preferred_recipes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_preferred_recipes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_preferred_recipes_id_seq OWNED BY public.customer_preferred_recipes.id;


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    company_name character varying(255),
    phone character varying(255),
    email character varying(255),
    notes text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: customers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customers_id_seq OWNED BY public.customers.id;


--
-- Name: failed_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.failed_jobs (
    id bigint NOT NULL,
    uuid character varying(255) NOT NULL,
    connection text NOT NULL,
    queue text NOT NULL,
    payload text NOT NULL,
    exception text NOT NULL,
    failed_at timestamp(0) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: failed_jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.failed_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: failed_jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.failed_jobs_id_seq OWNED BY public.failed_jobs.id;


--
-- Name: finished_product_components; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_components (
    id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    packaging_item_id bigint NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_components_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_components_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_components_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_components_id_seq OWNED BY public.finished_product_components.id;


--
-- Name: finished_product_recipe_attribute_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_recipe_attribute_rules (
    id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    attribute_definition_id bigint NOT NULL,
    value numeric(14,4) NOT NULL,
    min_deviation numeric(14,4),
    max_deviation numeric(14,4),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_recipe_attribute_rules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_recipe_attribute_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_recipe_attribute_rules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_recipe_attribute_rules_id_seq OWNED BY public.finished_product_recipe_attribute_rules.id;


--
-- Name: finished_product_recipe_contents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_recipe_contents (
    id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    production_product_type_id bigint NOT NULL,
    ratio numeric(5,4) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_recipe_contents_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_recipe_contents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_recipe_contents_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_recipe_contents_id_seq OWNED BY public.finished_product_recipe_contents.id;


--
-- Name: finished_product_recipes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_recipes (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_recipes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_recipes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_recipes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_recipes_id_seq OWNED BY public.finished_product_recipes.id;


--
-- Name: finished_product_run_attribute_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_run_attribute_values (
    id bigint NOT NULL,
    finished_product_run_id bigint NOT NULL,
    attribute_definition_id bigint NOT NULL,
    estimated_value numeric(14,4) NOT NULL,
    actual_value numeric(14,4),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_run_attribute_values_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_run_attribute_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_run_attribute_values_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_run_attribute_values_id_seq OWNED BY public.finished_product_run_attribute_values.id;


--
-- Name: finished_product_run_materials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_run_materials (
    id bigint NOT NULL,
    finished_product_run_id bigint NOT NULL,
    source_material_id bigint NOT NULL,
    quantity_used numeric(14,4) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_run_materials_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_run_materials_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_run_materials_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_run_materials_id_seq OWNED BY public.finished_product_run_materials.id;


--
-- Name: finished_product_run_packagings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_run_packagings (
    id bigint NOT NULL,
    finished_product_run_id bigint NOT NULL,
    packaging_item_id bigint NOT NULL,
    packaging_lot_id bigint NOT NULL,
    quantity integer NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_run_packagings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_run_packagings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_run_packagings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_run_packagings_id_seq OWNED BY public.finished_product_run_packagings.id;


--
-- Name: finished_product_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_runs (
    id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    quantity integer NOT NULL,
    status character varying(30) DEFAULT 'planned'::character varying NOT NULL,
    note text,
    started_at timestamp(0) without time zone,
    completed_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    source_storage_unit_id bigint
);


--
-- Name: finished_product_runs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_runs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_runs_id_seq OWNED BY public.finished_product_runs.id;


--
-- Name: finished_product_stock_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_stock_movements (
    id bigint NOT NULL,
    finished_product_storage_id bigint NOT NULL,
    finished_product_storage_slot_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    movement_type character varying(20) NOT NULL,
    quantity integer NOT NULL,
    counterparty_slot_id bigint,
    transfer_reference uuid,
    created_by_user_id bigint,
    note text,
    moved_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    finished_product_run_id bigint,
    order_id bigint
);


--
-- Name: finished_product_stock_movements_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_stock_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_stock_movements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_stock_movements_id_seq OWNED BY public.finished_product_stock_movements.id;


--
-- Name: finished_product_storage_slot_stocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_storage_slot_stocks (
    id bigint NOT NULL,
    finished_product_storage_slot_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_storage_slot_stocks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_storage_slot_stocks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_storage_slot_stocks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_storage_slot_stocks_id_seq OWNED BY public.finished_product_storage_slot_stocks.id;


--
-- Name: finished_product_storage_slots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_storage_slots (
    id bigint NOT NULL,
    finished_product_storage_id bigint NOT NULL,
    name character varying(50),
    volume_litre numeric(10,4) NOT NULL,
    max_quantity integer NOT NULL,
    current_quantity integer DEFAULT 0 NOT NULL,
    finished_product_id bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_storage_slots_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_storage_slots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_storage_slots_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_storage_slots_id_seq OWNED BY public.finished_product_storage_slots.id;


--
-- Name: finished_product_storage_stocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_storage_stocks (
    id bigint NOT NULL,
    finished_product_storage_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_storage_stocks_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_storage_stocks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_storage_stocks_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_storage_stocks_id_seq OWNED BY public.finished_product_storage_stocks.id;


--
-- Name: finished_product_storages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_product_storages (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_product_storages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_product_storages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_product_storages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_product_storages_id_seq OWNED BY public.finished_product_storages.id;


--
-- Name: finished_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finished_products (
    id bigint NOT NULL,
    finished_product_recipe_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    volume_litre numeric(10,4) NOT NULL,
    total_stock integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: finished_products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finished_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finished_products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.finished_products_id_seq OWNED BY public.finished_products.id;


--
-- Name: job_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.job_batches (
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    total_jobs integer NOT NULL,
    pending_jobs integer NOT NULL,
    failed_jobs integer NOT NULL,
    failed_job_ids text NOT NULL,
    options text,
    cancelled_at integer,
    created_at integer NOT NULL,
    finished_at integer
);


--
-- Name: jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.jobs (
    id bigint NOT NULL,
    queue character varying(255) NOT NULL,
    payload text NOT NULL,
    attempts smallint NOT NULL,
    reserved_at integer,
    available_at integer NOT NULL,
    created_at integer NOT NULL
);


--
-- Name: jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.jobs_id_seq OWNED BY public.jobs.id;


--
-- Name: material_attribute_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.material_attribute_values (
    id bigint NOT NULL,
    material_id bigint NOT NULL,
    production_product_attribute_definition_id bigint NOT NULL,
    value_boolean boolean,
    value_integer bigint,
    value_decimal numeric(14,4),
    value_text character varying(255),
    value_json json,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: material_attribute_values_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.material_attribute_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: material_attribute_values_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.material_attribute_values_id_seq OWNED BY public.material_attribute_values.id;


--
-- Name: materials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.materials (
    id bigint NOT NULL,
    production_batch_id bigint,
    production_product_type_id bigint NOT NULL,
    parent_material_id bigint,
    origin_type character varying(50) NOT NULL,
    quantity numeric(14,4) DEFAULT '0'::numeric NOT NULL,
    unit character varying(255) NOT NULL,
    status character varying(30) DEFAULT 'active'::character varying NOT NULL,
    current_location_type character varying(50),
    current_location_id bigint,
    source_production_stage_id bigint,
    created_by_batch_process_id bigint,
    closed_by_batch_process_id bigint,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT materials_unit_check CHECK (((unit)::text = ANY ((ARRAY['KG'::character varying, 'LITRE'::character varying])::text[])))
);


--
-- Name: materials_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.materials_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: materials_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.materials_id_seq OWNED BY public.materials.id;


--
-- Name: migrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.migrations (
    id integer NOT NULL,
    migration character varying(255) NOT NULL,
    batch integer NOT NULL
);


--
-- Name: migrations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.migrations_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: migrations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.migrations_id_seq OWNED BY public.migrations.id;


--
-- Name: model_has_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.model_has_permissions (
    permission_id bigint NOT NULL,
    model_type character varying(255) NOT NULL,
    model_id bigint NOT NULL
);


--
-- Name: model_has_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.model_has_roles (
    role_id bigint NOT NULL,
    model_type character varying(255) NOT NULL,
    model_id bigint NOT NULL
);


--
-- Name: order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_items (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    quantity integer NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: order_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.order_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.order_items_id_seq OWNED BY public.order_items.id;


--
-- Name: order_status_histories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_status_histories (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    from_status character varying(30),
    to_status character varying(30) NOT NULL,
    changed_by_user_id bigint,
    note text,
    changed_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: order_status_histories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.order_status_histories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: order_status_histories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.order_status_histories_id_seq OWNED BY public.order_status_histories.id;


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    delivery_address_id bigint NOT NULL,
    created_by_user_id bigint,
    status character varying(30) DEFAULT 'draft'::character varying NOT NULL,
    order_date date NOT NULL,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: orders_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.orders_id_seq OWNED BY public.orders.id;


--
-- Name: packaging_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.packaging_items (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(30) NOT NULL,
    current_stock integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: packaging_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.packaging_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: packaging_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.packaging_items_id_seq OWNED BY public.packaging_items.id;


--
-- Name: packaging_lot_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.packaging_lot_movements (
    id bigint NOT NULL,
    packaging_lot_id bigint NOT NULL,
    packaging_item_id bigint NOT NULL,
    finished_product_run_id bigint,
    user_id bigint,
    source_type character varying(50) NOT NULL,
    movement_type character varying(50) NOT NULL,
    quantity integer NOT NULL,
    note text,
    moved_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: packaging_lot_movements_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.packaging_lot_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: packaging_lot_movements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.packaging_lot_movements_id_seq OWNED BY public.packaging_lot_movements.id;


--
-- Name: packaging_lots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.packaging_lots (
    id bigint NOT NULL,
    packaging_item_id bigint NOT NULL,
    product_supplier_id bigint,
    lot_code character varying(100) NOT NULL,
    received_at timestamp(0) without time zone NOT NULL,
    quantity_received integer NOT NULL,
    quantity_remaining integer NOT NULL,
    status character varying(20) DEFAULT 'active'::character varying NOT NULL,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: packaging_lots_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.packaging_lots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: packaging_lots_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.packaging_lots_id_seq OWNED BY public.packaging_lots.id;


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    email character varying(255) NOT NULL,
    token character varying(255) NOT NULL,
    created_at timestamp(0) without time zone
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    guard_name character varying(255) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: permissions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permissions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.permissions_id_seq OWNED BY public.permissions.id;


--
-- Name: personal_access_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.personal_access_tokens (
    id bigint NOT NULL,
    tokenable_type character varying(255) NOT NULL,
    tokenable_id bigint NOT NULL,
    name text NOT NULL,
    token character varying(64) NOT NULL,
    abilities text,
    last_used_at timestamp(0) without time zone,
    expires_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: personal_access_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.personal_access_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: personal_access_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.personal_access_tokens_id_seq OWNED BY public.personal_access_tokens.id;


--
-- Name: process_type_fields; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_type_fields (
    id bigint NOT NULL,
    process_type_id bigint NOT NULL,
    field_key character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    data_type character varying(255) NOT NULL,
    is_required boolean DEFAULT false NOT NULL,
    unit character varying(255),
    sort_order integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    options json,
    default_value text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: process_type_fields_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.process_type_fields_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: process_type_fields_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.process_type_fields_id_seq OWNED BY public.process_type_fields.id;


--
-- Name: process_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.process_types (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    category character varying(255) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: process_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.process_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: process_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.process_types_id_seq OWNED BY public.process_types.id;


--
-- Name: product_suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_suppliers (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(50) NOT NULL,
    phone character varying(50),
    email character varying(255),
    address text,
    note text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: product_suppliers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.product_suppliers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: product_suppliers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.product_suppliers_id_seq OWNED BY public.product_suppliers.id;


--
-- Name: production_batch_input_production_product_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_batch_input_production_product_types (
    id bigint NOT NULL,
    production_batch_id bigint NOT NULL,
    production_product_type_id bigint NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: production_batch_input_production_product_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_batch_input_production_product_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_batch_input_production_product_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_batch_input_production_product_types_id_seq OWNED BY public.production_batch_input_production_product_types.id;


--
-- Name: production_batch_output_production_product_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_batch_output_production_product_types (
    id bigint NOT NULL,
    production_batch_id bigint NOT NULL,
    production_product_type_id bigint NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: production_batch_output_production_product_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_batch_output_production_product_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_batch_output_production_product_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_batch_output_production_product_types_id_seq OWNED BY public.production_batch_output_production_product_types.id;


--
-- Name: production_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_batches (
    id bigint NOT NULL,
    batch_code character varying(255) NOT NULL,
    production_line_id bigint NOT NULL,
    created_by_user_id bigint,
    status character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    quantity_kg numeric(14,4),
    received_at timestamp(0) without time zone,
    started_at timestamp(0) without time zone,
    completed_at timestamp(0) without time zone,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    supplier_id bigint,
    supplier_field_id bigint
);


--
-- Name: production_batches_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_batches_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_batches_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_batches_id_seq OWNED BY public.production_batches.id;


--
-- Name: production_line_input_production_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_line_input_production_products (
    id bigint NOT NULL,
    production_line_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    production_product_type_id bigint
);


--
-- Name: production_line_input_production_products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_line_input_production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_line_input_production_products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_line_input_production_products_id_seq OWNED BY public.production_line_input_production_products.id;


--
-- Name: production_line_output_production_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_line_output_production_products (
    id bigint NOT NULL,
    production_line_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    production_product_type_id bigint
);


--
-- Name: production_line_output_production_products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_line_output_production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_line_output_production_products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_line_output_production_products_id_seq OWNED BY public.production_line_output_production_products.id;


--
-- Name: production_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_lines (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: production_lines_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_lines_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_lines_id_seq OWNED BY public.production_lines.id;


--
-- Name: production_product_attribute_definitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_product_attribute_definitions (
    id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    attribute_definition_id bigint NOT NULL,
    is_required boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: production_product_attribute_definitions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_product_attribute_definitions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_product_attribute_definitions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_product_attribute_definitions_id_seq OWNED BY public.production_product_attribute_definitions.id;


--
-- Name: production_product_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_product_types (
    id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    type character varying(255),
    unit character varying(255) DEFAULT 'LITRE'::character varying NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    density_kg_per_litre numeric(6,4),
    CONSTRAINT production_product_types_unit_check CHECK (((unit)::text = ANY ((ARRAY['KG'::character varying, 'LITRE'::character varying])::text[])))
);


--
-- Name: production_product_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_product_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_product_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_product_types_id_seq OWNED BY public.production_product_types.id;


--
-- Name: production_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_products (
    id bigint NOT NULL,
    key character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    default_density_kg_per_litre numeric(6,4)
);


--
-- Name: production_products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_products_id_seq OWNED BY public.production_products.id;


--
-- Name: production_stage_extension_input_production_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_stage_extension_input_production_products (
    id bigint NOT NULL,
    production_stage_extension_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    is_required boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: production_stage_extension_input_production_products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_stage_extension_input_production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_stage_extension_input_production_products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_stage_extension_input_production_products_id_seq OWNED BY public.production_stage_extension_input_production_products.id;


--
-- Name: production_stage_extension_output_production_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_stage_extension_output_production_products (
    id bigint NOT NULL,
    production_stage_extension_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    is_required boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: production_stage_extension_output_production_products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_stage_extension_output_production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_stage_extension_output_production_products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_stage_extension_output_production_products_id_seq OWNED BY public.production_stage_extension_output_production_products.id;


--
-- Name: production_stage_extension_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_stage_extension_values (
    id bigint NOT NULL,
    production_stage_extension_id bigint NOT NULL,
    stage_extension_type_field_id bigint NOT NULL,
    value_boolean boolean,
    value_integer bigint,
    value_decimal numeric(14,4),
    value_text character varying(255),
    value_json json,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: production_stage_extension_values_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_stage_extension_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_stage_extension_values_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_stage_extension_values_id_seq OWNED BY public.production_stage_extension_values.id;


--
-- Name: production_stage_extensions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_stage_extensions (
    id bigint NOT NULL,
    production_stage_id bigint NOT NULL,
    stage_extension_type_id bigint NOT NULL,
    is_required boolean DEFAULT false NOT NULL,
    is_enabled boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: production_stage_extensions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_stage_extensions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_stage_extensions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_stage_extensions_id_seq OWNED BY public.production_stage_extensions.id;


--
-- Name: production_stages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_stages (
    id bigint NOT NULL,
    production_line_id bigint NOT NULL,
    process_type_id bigint,
    name character varying(255),
    stage_order integer NOT NULL,
    is_required boolean DEFAULT true NOT NULL,
    requires_approval boolean DEFAULT false NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: production_stages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.production_stages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: production_stages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.production_stages_id_seq OWNED BY public.production_stages.id;


--
-- Name: role_has_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_has_permissions (
    permission_id bigint NOT NULL,
    role_id bigint NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    guard_name character varying(255) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sessions (
    id character varying(255) NOT NULL,
    user_id bigint,
    ip_address character varying(45),
    user_agent text,
    payload text NOT NULL,
    last_activity integer NOT NULL
);


--
-- Name: stage_extension_type_fields; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stage_extension_type_fields (
    id bigint NOT NULL,
    stage_extension_type_id bigint NOT NULL,
    field_key character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    data_type character varying(255) NOT NULL,
    is_required boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    default_value_boolean boolean,
    default_value_integer bigint,
    default_value_decimal numeric(14,4),
    default_value_text character varying(255),
    default_value_json json,
    options_json json,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: stage_extension_type_fields_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stage_extension_type_fields_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stage_extension_type_fields_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stage_extension_type_fields_id_seq OWNED BY public.stage_extension_type_fields.id;


--
-- Name: stage_extension_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stage_extension_types (
    id bigint NOT NULL,
    key character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: stage_extension_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stage_extension_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stage_extension_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stage_extension_types_id_seq OWNED BY public.stage_extension_types.id;


--
-- Name: storage_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_movements (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    material_id bigint NOT NULL,
    batch_process_id bigint,
    counterparty_storage_unit_id bigint,
    source_type character varying(50) NOT NULL,
    movement_type character varying(50) NOT NULL,
    transfer_reference character varying(255),
    quantity numeric(14,2) NOT NULL,
    unit character varying(255) NOT NULL,
    note text,
    moved_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    finished_product_run_id bigint,
    CONSTRAINT storage_movements_unit_check CHECK (((unit)::text = ANY ((ARRAY['KG'::character varying, 'LITRE'::character varying])::text[])))
);


--
-- Name: storage_movements_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_movements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.storage_movements_id_seq OWNED BY public.storage_movements.id;


--
-- Name: storage_unit_allowed_production_product_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_unit_allowed_production_product_types (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    production_product_type_id bigint NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: storage_unit_allowed_production_product_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_unit_allowed_production_product_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_unit_allowed_production_product_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.storage_unit_allowed_production_product_types_id_seq OWNED BY public.storage_unit_allowed_production_product_types.id;


--
-- Name: storage_unit_allowed_production_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_unit_allowed_production_products (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: storage_unit_allowed_production_products_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_unit_allowed_production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_unit_allowed_production_products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.storage_unit_allowed_production_products_id_seq OWNED BY public.storage_unit_allowed_production_products.id;


--
-- Name: storage_unit_attribute_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_unit_attribute_rules (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    production_product_attribute_definition_id bigint NOT NULL,
    min_value_decimal numeric(15,4),
    max_value_decimal numeric(15,4),
    apply_mode character varying(255) DEFAULT 'incoming_and_average'::character varying NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT storage_unit_attribute_rules_apply_mode_check CHECK (((apply_mode)::text = ANY ((ARRAY['incoming_only'::character varying, 'tank_average_only'::character varying, 'incoming_and_average'::character varying])::text[])))
);


--
-- Name: storage_unit_attribute_rules_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_unit_attribute_rules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_unit_attribute_rules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.storage_unit_attribute_rules_id_seq OWNED BY public.storage_unit_attribute_rules.id;


--
-- Name: storage_unit_balances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_unit_balances (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    current_quantity numeric(14,2) DEFAULT '0'::numeric NOT NULL,
    unit character varying(255) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT storage_unit_balances_unit_check CHECK (((unit)::text = ANY ((ARRAY['KG'::character varying, 'LITRE'::character varying])::text[])))
);


--
-- Name: storage_unit_balances_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_unit_balances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_unit_balances_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.storage_unit_balances_id_seq OWNED BY public.storage_unit_balances.id;


--
-- Name: storage_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_units (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    capacity numeric(14,2) NOT NULL,
    capacity_unit character varying(255) NOT NULL,
    is_blend_tank boolean DEFAULT false NOT NULL,
    allow_multiple_production_product_types boolean DEFAULT false NOT NULL,
    allow_multiple_production_products boolean DEFAULT false NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT storage_units_capacity_unit_check CHECK (((capacity_unit)::text = ANY ((ARRAY['KG'::character varying, 'LITRE'::character varying])::text[])))
);


--
-- Name: storage_units_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.storage_units_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: storage_units_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.storage_units_id_seq OWNED BY public.storage_units.id;


--
-- Name: supplier_fields; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.supplier_fields (
    id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    location character varying(255),
    area_decare numeric(10,2),
    note text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: supplier_fields_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.supplier_fields_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: supplier_fields_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.supplier_fields_id_seq OWNED BY public.supplier_fields.id;


--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.suppliers (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(50) NOT NULL,
    phone character varying(50),
    email character varying(255),
    address text,
    note text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: suppliers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.suppliers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: suppliers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.suppliers_id_seq OWNED BY public.suppliers.id;


--
-- Name: user_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_notifications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    sent_by_user_id bigint,
    type character varying(40) DEFAULT 'nudge'::character varying NOT NULL,
    channel character varying(20) DEFAULT 'in_app'::character varying NOT NULL,
    title character varying(255) NOT NULL,
    body text,
    subject_type character varying(60),
    subject_id bigint,
    read_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: user_notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_notifications_id_seq OWNED BY public.user_notifications.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    email character varying(255) NOT NULL,
    email_verified_at timestamp(0) without time zone,
    password character varying(255) NOT NULL,
    remember_token character varying(100),
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: analytics_reports id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.analytics_reports ALTER COLUMN id SET DEFAULT nextval('public.analytics_reports_id_seq'::regclass);


--
-- Name: attribute_definitions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attribute_definitions ALTER COLUMN id SET DEFAULT nextval('public.attribute_definitions_id_seq'::regclass);


--
-- Name: batch_process_field_values id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_field_values ALTER COLUMN id SET DEFAULT nextval('public.batch_process_field_values_id_seq'::regclass);


--
-- Name: batch_process_reviews id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_reviews ALTER COLUMN id SET DEFAULT nextval('public.batch_process_reviews_id_seq'::regclass);


--
-- Name: batch_processes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_processes ALTER COLUMN id SET DEFAULT nextval('public.batch_processes_id_seq'::regclass);


--
-- Name: customer_addresses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_addresses ALTER COLUMN id SET DEFAULT nextval('public.customer_addresses_id_seq'::regclass);


--
-- Name: customer_preferred_recipes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_preferred_recipes ALTER COLUMN id SET DEFAULT nextval('public.customer_preferred_recipes_id_seq'::regclass);


--
-- Name: customers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers ALTER COLUMN id SET DEFAULT nextval('public.customers_id_seq'::regclass);


--
-- Name: failed_jobs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.failed_jobs ALTER COLUMN id SET DEFAULT nextval('public.failed_jobs_id_seq'::regclass);


--
-- Name: finished_product_components id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_components ALTER COLUMN id SET DEFAULT nextval('public.finished_product_components_id_seq'::regclass);


--
-- Name: finished_product_recipe_attribute_rules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_attribute_rules ALTER COLUMN id SET DEFAULT nextval('public.finished_product_recipe_attribute_rules_id_seq'::regclass);


--
-- Name: finished_product_recipe_contents id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_contents ALTER COLUMN id SET DEFAULT nextval('public.finished_product_recipe_contents_id_seq'::regclass);


--
-- Name: finished_product_recipes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipes ALTER COLUMN id SET DEFAULT nextval('public.finished_product_recipes_id_seq'::regclass);


--
-- Name: finished_product_run_attribute_values id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_attribute_values ALTER COLUMN id SET DEFAULT nextval('public.finished_product_run_attribute_values_id_seq'::regclass);


--
-- Name: finished_product_run_materials id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_materials ALTER COLUMN id SET DEFAULT nextval('public.finished_product_run_materials_id_seq'::regclass);


--
-- Name: finished_product_run_packagings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_packagings ALTER COLUMN id SET DEFAULT nextval('public.finished_product_run_packagings_id_seq'::regclass);


--
-- Name: finished_product_runs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_runs ALTER COLUMN id SET DEFAULT nextval('public.finished_product_runs_id_seq'::regclass);


--
-- Name: finished_product_stock_movements id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_stock_movements ALTER COLUMN id SET DEFAULT nextval('public.finished_product_stock_movements_id_seq'::regclass);


--
-- Name: finished_product_storage_slot_stocks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_slot_stocks ALTER COLUMN id SET DEFAULT nextval('public.finished_product_storage_slot_stocks_id_seq'::regclass);


--
-- Name: finished_product_storage_slots id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_slots ALTER COLUMN id SET DEFAULT nextval('public.finished_product_storage_slots_id_seq'::regclass);


--
-- Name: finished_product_storage_stocks id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_stocks ALTER COLUMN id SET DEFAULT nextval('public.finished_product_storage_stocks_id_seq'::regclass);


--
-- Name: finished_product_storages id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storages ALTER COLUMN id SET DEFAULT nextval('public.finished_product_storages_id_seq'::regclass);


--
-- Name: finished_products id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_products ALTER COLUMN id SET DEFAULT nextval('public.finished_products_id_seq'::regclass);


--
-- Name: jobs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jobs ALTER COLUMN id SET DEFAULT nextval('public.jobs_id_seq'::regclass);


--
-- Name: material_attribute_values id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_attribute_values ALTER COLUMN id SET DEFAULT nextval('public.material_attribute_values_id_seq'::regclass);


--
-- Name: materials id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials ALTER COLUMN id SET DEFAULT nextval('public.materials_id_seq'::regclass);


--
-- Name: migrations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.migrations ALTER COLUMN id SET DEFAULT nextval('public.migrations_id_seq'::regclass);


--
-- Name: order_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items ALTER COLUMN id SET DEFAULT nextval('public.order_items_id_seq'::regclass);


--
-- Name: order_status_histories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_histories ALTER COLUMN id SET DEFAULT nextval('public.order_status_histories_id_seq'::regclass);


--
-- Name: orders id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders ALTER COLUMN id SET DEFAULT nextval('public.orders_id_seq'::regclass);


--
-- Name: packaging_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_items ALTER COLUMN id SET DEFAULT nextval('public.packaging_items_id_seq'::regclass);


--
-- Name: packaging_lot_movements id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lot_movements ALTER COLUMN id SET DEFAULT nextval('public.packaging_lot_movements_id_seq'::regclass);


--
-- Name: packaging_lots id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lots ALTER COLUMN id SET DEFAULT nextval('public.packaging_lots_id_seq'::regclass);


--
-- Name: permissions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions ALTER COLUMN id SET DEFAULT nextval('public.permissions_id_seq'::regclass);


--
-- Name: personal_access_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_access_tokens ALTER COLUMN id SET DEFAULT nextval('public.personal_access_tokens_id_seq'::regclass);


--
-- Name: process_type_fields id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_type_fields ALTER COLUMN id SET DEFAULT nextval('public.process_type_fields_id_seq'::regclass);


--
-- Name: process_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_types ALTER COLUMN id SET DEFAULT nextval('public.process_types_id_seq'::regclass);


--
-- Name: product_suppliers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_suppliers ALTER COLUMN id SET DEFAULT nextval('public.product_suppliers_id_seq'::regclass);


--
-- Name: production_batch_input_production_product_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batch_input_production_product_types ALTER COLUMN id SET DEFAULT nextval('public.production_batch_input_production_product_types_id_seq'::regclass);


--
-- Name: production_batch_output_production_product_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batch_output_production_product_types ALTER COLUMN id SET DEFAULT nextval('public.production_batch_output_production_product_types_id_seq'::regclass);


--
-- Name: production_batches id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches ALTER COLUMN id SET DEFAULT nextval('public.production_batches_id_seq'::regclass);


--
-- Name: production_line_input_production_products id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_input_production_products ALTER COLUMN id SET DEFAULT nextval('public.production_line_input_production_products_id_seq'::regclass);


--
-- Name: production_line_output_production_products id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_output_production_products ALTER COLUMN id SET DEFAULT nextval('public.production_line_output_production_products_id_seq'::regclass);


--
-- Name: production_lines id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_lines ALTER COLUMN id SET DEFAULT nextval('public.production_lines_id_seq'::regclass);


--
-- Name: production_product_attribute_definitions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_product_attribute_definitions ALTER COLUMN id SET DEFAULT nextval('public.production_product_attribute_definitions_id_seq'::regclass);


--
-- Name: production_product_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_product_types ALTER COLUMN id SET DEFAULT nextval('public.production_product_types_id_seq'::regclass);


--
-- Name: production_products id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_products ALTER COLUMN id SET DEFAULT nextval('public.production_products_id_seq'::regclass);


--
-- Name: production_stage_extension_input_production_products id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_input_production_products ALTER COLUMN id SET DEFAULT nextval('public.production_stage_extension_input_production_products_id_seq'::regclass);


--
-- Name: production_stage_extension_output_production_products id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_output_production_products ALTER COLUMN id SET DEFAULT nextval('public.production_stage_extension_output_production_products_id_seq'::regclass);


--
-- Name: production_stage_extension_values id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_values ALTER COLUMN id SET DEFAULT nextval('public.production_stage_extension_values_id_seq'::regclass);


--
-- Name: production_stage_extensions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extensions ALTER COLUMN id SET DEFAULT nextval('public.production_stage_extensions_id_seq'::regclass);


--
-- Name: production_stages id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stages ALTER COLUMN id SET DEFAULT nextval('public.production_stages_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: stage_extension_type_fields id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stage_extension_type_fields ALTER COLUMN id SET DEFAULT nextval('public.stage_extension_type_fields_id_seq'::regclass);


--
-- Name: stage_extension_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stage_extension_types ALTER COLUMN id SET DEFAULT nextval('public.stage_extension_types_id_seq'::regclass);


--
-- Name: storage_movements id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_movements ALTER COLUMN id SET DEFAULT nextval('public.storage_movements_id_seq'::regclass);


--
-- Name: storage_unit_allowed_production_product_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_product_types ALTER COLUMN id SET DEFAULT nextval('public.storage_unit_allowed_production_product_types_id_seq'::regclass);


--
-- Name: storage_unit_allowed_production_products id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_products ALTER COLUMN id SET DEFAULT nextval('public.storage_unit_allowed_production_products_id_seq'::regclass);


--
-- Name: storage_unit_attribute_rules id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_attribute_rules ALTER COLUMN id SET DEFAULT nextval('public.storage_unit_attribute_rules_id_seq'::regclass);


--
-- Name: storage_unit_balances id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_balances ALTER COLUMN id SET DEFAULT nextval('public.storage_unit_balances_id_seq'::regclass);


--
-- Name: storage_units id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_units ALTER COLUMN id SET DEFAULT nextval('public.storage_units_id_seq'::regclass);


--
-- Name: supplier_fields id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_fields ALTER COLUMN id SET DEFAULT nextval('public.supplier_fields_id_seq'::regclass);


--
-- Name: suppliers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers ALTER COLUMN id SET DEFAULT nextval('public.suppliers_id_seq'::regclass);


--
-- Name: user_notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notifications ALTER COLUMN id SET DEFAULT nextval('public.user_notifications_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: analytics_reports analytics_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.analytics_reports
    ADD CONSTRAINT analytics_reports_pkey PRIMARY KEY (id);


--
-- Name: attribute_definitions attribute_definitions_attribute_key_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attribute_definitions
    ADD CONSTRAINT attribute_definitions_attribute_key_unique UNIQUE (attribute_key);


--
-- Name: attribute_definitions attribute_definitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attribute_definitions
    ADD CONSTRAINT attribute_definitions_pkey PRIMARY KEY (id);


--
-- Name: batch_process_field_values batch_process_field_values_batch_process_id_process_type_field_; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_field_values
    ADD CONSTRAINT batch_process_field_values_batch_process_id_process_type_field_ UNIQUE (batch_process_id, process_type_field_id);


--
-- Name: batch_process_field_values batch_process_field_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_field_values
    ADD CONSTRAINT batch_process_field_values_pkey PRIMARY KEY (id);


--
-- Name: batch_process_reviews batch_process_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_reviews
    ADD CONSTRAINT batch_process_reviews_pkey PRIMARY KEY (id);


--
-- Name: batch_processes batch_processes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_processes
    ADD CONSTRAINT batch_processes_pkey PRIMARY KEY (id);


--
-- Name: batch_processes batch_processes_production_batch_id_production_stage_id_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_processes
    ADD CONSTRAINT batch_processes_production_batch_id_production_stage_id_unique UNIQUE (production_batch_id, production_stage_id);


--
-- Name: cache_locks cache_locks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cache_locks
    ADD CONSTRAINT cache_locks_pkey PRIMARY KEY (key);


--
-- Name: cache cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cache
    ADD CONSTRAINT cache_pkey PRIMARY KEY (key);


--
-- Name: customer_addresses customer_addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_addresses
    ADD CONSTRAINT customer_addresses_pkey PRIMARY KEY (id);


--
-- Name: customer_preferred_recipes customer_preferred_recipe_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_preferred_recipes
    ADD CONSTRAINT customer_preferred_recipe_unique UNIQUE (customer_id, finished_product_recipe_id);


--
-- Name: customer_preferred_recipes customer_preferred_recipes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_preferred_recipes
    ADD CONSTRAINT customer_preferred_recipes_pkey PRIMARY KEY (id);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);


--
-- Name: failed_jobs failed_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.failed_jobs
    ADD CONSTRAINT failed_jobs_pkey PRIMARY KEY (id);


--
-- Name: failed_jobs failed_jobs_uuid_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.failed_jobs
    ADD CONSTRAINT failed_jobs_uuid_unique UNIQUE (uuid);


--
-- Name: finished_product_components finished_product_components_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_components
    ADD CONSTRAINT finished_product_components_pkey PRIMARY KEY (id);


--
-- Name: finished_product_recipe_attribute_rules finished_product_recipe_attribute_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_attribute_rules
    ADD CONSTRAINT finished_product_recipe_attribute_rules_pkey PRIMARY KEY (id);


--
-- Name: finished_product_recipe_contents finished_product_recipe_contents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_contents
    ADD CONSTRAINT finished_product_recipe_contents_pkey PRIMARY KEY (id);


--
-- Name: finished_product_recipes finished_product_recipes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipes
    ADD CONSTRAINT finished_product_recipes_pkey PRIMARY KEY (id);


--
-- Name: finished_product_run_attribute_values finished_product_run_attribute_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_attribute_values
    ADD CONSTRAINT finished_product_run_attribute_values_pkey PRIMARY KEY (id);


--
-- Name: finished_product_run_materials finished_product_run_materials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_materials
    ADD CONSTRAINT finished_product_run_materials_pkey PRIMARY KEY (id);


--
-- Name: finished_product_run_packagings finished_product_run_packagings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_packagings
    ADD CONSTRAINT finished_product_run_packagings_pkey PRIMARY KEY (id);


--
-- Name: finished_product_runs finished_product_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_runs
    ADD CONSTRAINT finished_product_runs_pkey PRIMARY KEY (id);


--
-- Name: finished_product_stock_movements finished_product_stock_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_pkey PRIMARY KEY (id);


--
-- Name: finished_product_storage_slot_stocks finished_product_storage_slot_stocks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_slot_stocks
    ADD CONSTRAINT finished_product_storage_slot_stocks_pkey PRIMARY KEY (id);


--
-- Name: finished_product_storage_slots finished_product_storage_slots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_slots
    ADD CONSTRAINT finished_product_storage_slots_pkey PRIMARY KEY (id);


--
-- Name: finished_product_storage_stocks finished_product_storage_stocks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_stocks
    ADD CONSTRAINT finished_product_storage_stocks_pkey PRIMARY KEY (id);


--
-- Name: finished_product_storages finished_product_storages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storages
    ADD CONSTRAINT finished_product_storages_pkey PRIMARY KEY (id);


--
-- Name: finished_products finished_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_products
    ADD CONSTRAINT finished_products_pkey PRIMARY KEY (id);


--
-- Name: finished_product_components fp_component_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_components
    ADD CONSTRAINT fp_component_unique UNIQUE (finished_product_id, packaging_item_id);


--
-- Name: finished_product_run_attribute_values fp_run_attr_value_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_attribute_values
    ADD CONSTRAINT fp_run_attr_value_unique UNIQUE (finished_product_run_id, attribute_definition_id);


--
-- Name: finished_product_storage_slot_stocks fp_slot_stock_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_slot_stocks
    ADD CONSTRAINT fp_slot_stock_unique UNIQUE (finished_product_storage_slot_id, finished_product_id);


--
-- Name: finished_product_storage_stocks fp_storage_stock_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_stocks
    ADD CONSTRAINT fp_storage_stock_unique UNIQUE (finished_product_storage_id, finished_product_id);


--
-- Name: finished_product_recipe_attribute_rules fpr_attr_rule_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_attribute_rules
    ADD CONSTRAINT fpr_attr_rule_unique UNIQUE (finished_product_recipe_id, attribute_definition_id);


--
-- Name: finished_product_recipe_contents fpr_content_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_contents
    ADD CONSTRAINT fpr_content_unique UNIQUE (finished_product_recipe_id, production_product_type_id);


--
-- Name: finished_product_run_packagings fpr_pkg_run_lot_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_packagings
    ADD CONSTRAINT fpr_pkg_run_lot_unique UNIQUE (finished_product_run_id, packaging_lot_id);


--
-- Name: job_batches job_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.job_batches
    ADD CONSTRAINT job_batches_pkey PRIMARY KEY (id);


--
-- Name: jobs jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT jobs_pkey PRIMARY KEY (id);


--
-- Name: material_attribute_values material_attr_value_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_attribute_values
    ADD CONSTRAINT material_attr_value_unique UNIQUE (material_id, production_product_attribute_definition_id);


--
-- Name: material_attribute_values material_attribute_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_attribute_values
    ADD CONSTRAINT material_attribute_values_pkey PRIMARY KEY (id);


--
-- Name: materials materials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_pkey PRIMARY KEY (id);


--
-- Name: migrations migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.migrations
    ADD CONSTRAINT migrations_pkey PRIMARY KEY (id);


--
-- Name: model_has_permissions model_has_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.model_has_permissions
    ADD CONSTRAINT model_has_permissions_pkey PRIMARY KEY (permission_id, model_id, model_type);


--
-- Name: model_has_roles model_has_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.model_has_roles
    ADD CONSTRAINT model_has_roles_pkey PRIMARY KEY (role_id, model_id, model_type);


--
-- Name: order_items order_item_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_item_unique UNIQUE (order_id, finished_product_id);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);


--
-- Name: order_status_histories order_status_histories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_histories
    ADD CONSTRAINT order_status_histories_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: packaging_items packaging_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_items
    ADD CONSTRAINT packaging_items_pkey PRIMARY KEY (id);


--
-- Name: packaging_lot_movements packaging_lot_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_pkey PRIMARY KEY (id);


--
-- Name: packaging_lots packaging_lots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lots
    ADD CONSTRAINT packaging_lots_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (email);


--
-- Name: permissions permissions_name_guard_name_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_name_guard_name_unique UNIQUE (name, guard_name);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: personal_access_tokens personal_access_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_access_tokens
    ADD CONSTRAINT personal_access_tokens_pkey PRIMARY KEY (id);


--
-- Name: personal_access_tokens personal_access_tokens_token_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_access_tokens
    ADD CONSTRAINT personal_access_tokens_token_unique UNIQUE (token);


--
-- Name: packaging_lots pkg_lots_item_lot_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lots
    ADD CONSTRAINT pkg_lots_item_lot_unique UNIQUE (packaging_item_id, lot_code);


--
-- Name: process_type_fields process_type_fields_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_type_fields
    ADD CONSTRAINT process_type_fields_pkey PRIMARY KEY (id);


--
-- Name: process_type_fields process_type_fields_process_type_id_field_key_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_type_fields
    ADD CONSTRAINT process_type_fields_process_type_id_field_key_unique UNIQUE (process_type_id, field_key);


--
-- Name: process_types process_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_types
    ADD CONSTRAINT process_types_pkey PRIMARY KEY (id);


--
-- Name: production_stage_extension_input_production_products prod_stage_ext_input_product_type_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_input_production_products
    ADD CONSTRAINT prod_stage_ext_input_product_type_unique UNIQUE (production_stage_extension_id, production_product_id);


--
-- Name: production_stage_extension_output_production_products prod_stage_ext_output_product_type_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_output_production_products
    ADD CONSTRAINT prod_stage_ext_output_product_type_unique UNIQUE (production_stage_extension_id, production_product_id);


--
-- Name: production_stage_extensions prod_stage_ext_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extensions
    ADD CONSTRAINT prod_stage_ext_unique UNIQUE (production_stage_id, stage_extension_type_id);


--
-- Name: production_stage_extension_values prod_stage_ext_value_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_values
    ADD CONSTRAINT prod_stage_ext_value_unique UNIQUE (production_stage_extension_id, stage_extension_type_field_id);


--
-- Name: production_product_attribute_definitions product_attr_def_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_product_attribute_definitions
    ADD CONSTRAINT product_attr_def_unique UNIQUE (production_product_id, attribute_definition_id);


--
-- Name: product_suppliers product_suppliers_code_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_suppliers
    ADD CONSTRAINT product_suppliers_code_unique UNIQUE (code);


--
-- Name: product_suppliers product_suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_suppliers
    ADD CONSTRAINT product_suppliers_pkey PRIMARY KEY (id);


--
-- Name: production_batch_input_production_product_types production_batch_input_production_product_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batch_input_production_product_types
    ADD CONSTRAINT production_batch_input_production_product_types_pkey PRIMARY KEY (id);


--
-- Name: production_batch_output_production_product_types production_batch_output_production_product_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batch_output_production_product_types
    ADD CONSTRAINT production_batch_output_production_product_types_pkey PRIMARY KEY (id);


--
-- Name: production_batches production_batches_batch_code_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT production_batches_batch_code_unique UNIQUE (batch_code);


--
-- Name: production_batches production_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT production_batches_pkey PRIMARY KEY (id);


--
-- Name: production_line_input_production_products production_line_input_product_type_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_input_production_products
    ADD CONSTRAINT production_line_input_product_type_unique UNIQUE (production_line_id, production_product_id);


--
-- Name: production_line_input_production_products production_line_input_production_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_input_production_products
    ADD CONSTRAINT production_line_input_production_products_pkey PRIMARY KEY (id);


--
-- Name: production_line_output_production_products production_line_output_product_type_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_output_production_products
    ADD CONSTRAINT production_line_output_product_type_unique UNIQUE (production_line_id, production_product_id);


--
-- Name: production_line_output_production_products production_line_output_production_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_output_production_products
    ADD CONSTRAINT production_line_output_production_products_pkey PRIMARY KEY (id);


--
-- Name: production_lines production_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_lines
    ADD CONSTRAINT production_lines_pkey PRIMARY KEY (id);


--
-- Name: production_product_attribute_definitions production_product_attribute_definitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_product_attribute_definitions
    ADD CONSTRAINT production_product_attribute_definitions_pkey PRIMARY KEY (id);


--
-- Name: production_product_types production_product_types_code_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_product_types
    ADD CONSTRAINT production_product_types_code_unique UNIQUE (code);


--
-- Name: production_product_types production_product_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_product_types
    ADD CONSTRAINT production_product_types_pkey PRIMARY KEY (id);


--
-- Name: production_products production_products_key_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_products
    ADD CONSTRAINT production_products_key_unique UNIQUE (key);


--
-- Name: production_products production_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_products
    ADD CONSTRAINT production_products_pkey PRIMARY KEY (id);


--
-- Name: production_stage_extension_input_production_products production_stage_extension_input_production_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_input_production_products
    ADD CONSTRAINT production_stage_extension_input_production_products_pkey PRIMARY KEY (id);


--
-- Name: production_stage_extension_output_production_products production_stage_extension_output_production_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_output_production_products
    ADD CONSTRAINT production_stage_extension_output_production_products_pkey PRIMARY KEY (id);


--
-- Name: production_stage_extension_values production_stage_extension_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_values
    ADD CONSTRAINT production_stage_extension_values_pkey PRIMARY KEY (id);


--
-- Name: production_stage_extensions production_stage_extensions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extensions
    ADD CONSTRAINT production_stage_extensions_pkey PRIMARY KEY (id);


--
-- Name: production_stages production_stages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stages
    ADD CONSTRAINT production_stages_pkey PRIMARY KEY (id);


--
-- Name: production_stages production_stages_production_line_id_stage_order_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stages
    ADD CONSTRAINT production_stages_production_line_id_stage_order_unique UNIQUE (production_line_id, stage_order);


--
-- Name: role_has_permissions role_has_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_has_permissions
    ADD CONSTRAINT role_has_permissions_pkey PRIMARY KEY (permission_id, role_id);


--
-- Name: roles roles_name_guard_name_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_name_guard_name_unique UNIQUE (name, guard_name);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: sessions sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_pkey PRIMARY KEY (id);


--
-- Name: stage_extension_type_fields stage_ext_type_field_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stage_extension_type_fields
    ADD CONSTRAINT stage_ext_type_field_unique UNIQUE (stage_extension_type_id, field_key);


--
-- Name: stage_extension_type_fields stage_extension_type_fields_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stage_extension_type_fields
    ADD CONSTRAINT stage_extension_type_fields_pkey PRIMARY KEY (id);


--
-- Name: stage_extension_types stage_extension_types_key_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stage_extension_types
    ADD CONSTRAINT stage_extension_types_key_unique UNIQUE (key);


--
-- Name: stage_extension_types stage_extension_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stage_extension_types
    ADD CONSTRAINT stage_extension_types_pkey PRIMARY KEY (id);


--
-- Name: storage_movements storage_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_movements
    ADD CONSTRAINT storage_movements_pkey PRIMARY KEY (id);


--
-- Name: storage_unit_allowed_production_product_types storage_unit_allowed_production_product_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_product_types
    ADD CONSTRAINT storage_unit_allowed_production_product_types_pkey PRIMARY KEY (id);


--
-- Name: storage_unit_allowed_production_product_types storage_unit_allowed_production_product_types_storage_unit_id_p; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_product_types
    ADD CONSTRAINT storage_unit_allowed_production_product_types_storage_unit_id_p UNIQUE (storage_unit_id, production_product_type_id);


--
-- Name: storage_unit_allowed_production_products storage_unit_allowed_production_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_products
    ADD CONSTRAINT storage_unit_allowed_production_products_pkey PRIMARY KEY (id);


--
-- Name: storage_unit_allowed_production_products storage_unit_allowed_production_products_storage_unit_id_produc; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_products
    ADD CONSTRAINT storage_unit_allowed_production_products_storage_unit_id_produc UNIQUE (storage_unit_id, production_product_id);


--
-- Name: storage_unit_attribute_rules storage_unit_attr_rules_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_attribute_rules
    ADD CONSTRAINT storage_unit_attr_rules_unique UNIQUE (storage_unit_id, production_product_attribute_definition_id);


--
-- Name: storage_unit_attribute_rules storage_unit_attribute_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_attribute_rules
    ADD CONSTRAINT storage_unit_attribute_rules_pkey PRIMARY KEY (id);


--
-- Name: storage_unit_balances storage_unit_balances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_balances
    ADD CONSTRAINT storage_unit_balances_pkey PRIMARY KEY (id);


--
-- Name: storage_unit_balances storage_unit_balances_storage_unit_id_production_product_id_uni; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_balances
    ADD CONSTRAINT storage_unit_balances_storage_unit_id_production_product_id_uni UNIQUE (storage_unit_id, production_product_id, unit);


--
-- Name: storage_units storage_units_code_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_units
    ADD CONSTRAINT storage_units_code_unique UNIQUE (code);


--
-- Name: storage_units storage_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_units
    ADD CONSTRAINT storage_units_pkey PRIMARY KEY (id);


--
-- Name: supplier_fields supplier_fields_code_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_fields
    ADD CONSTRAINT supplier_fields_code_unique UNIQUE (code);


--
-- Name: supplier_fields supplier_fields_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_fields
    ADD CONSTRAINT supplier_fields_pkey PRIMARY KEY (id);


--
-- Name: suppliers suppliers_code_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_code_unique UNIQUE (code);


--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);


--
-- Name: user_notifications user_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notifications
    ADD CONSTRAINT user_notifications_pkey PRIMARY KEY (id);


--
-- Name: users users_email_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_unique UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: analytics_reports_user_id_updated_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX analytics_reports_user_id_updated_at_index ON public.analytics_reports USING btree (user_id, updated_at);


--
-- Name: batch_process_reviews_batch_process_id_reviewed_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX batch_process_reviews_batch_process_id_reviewed_at_index ON public.batch_process_reviews USING btree (batch_process_id, reviewed_at);


--
-- Name: batch_processes_operator_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX batch_processes_operator_id_index ON public.batch_processes USING btree (operator_id);


--
-- Name: batch_processes_production_batch_id_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX batch_processes_production_batch_id_status_index ON public.batch_processes USING btree (production_batch_id, status);


--
-- Name: cache_expiration_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cache_expiration_index ON public.cache USING btree (expiration);


--
-- Name: cache_locks_expiration_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cache_locks_expiration_index ON public.cache_locks USING btree (expiration);


--
-- Name: customer_addresses_customer_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_addresses_customer_id_index ON public.customer_addresses USING btree (customer_id);


--
-- Name: finished_product_recipes_is_active_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_recipes_is_active_index ON public.finished_product_recipes USING btree (is_active);


--
-- Name: finished_product_recipes_name_version_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_recipes_name_version_index ON public.finished_product_recipes USING btree (name, version);


--
-- Name: finished_product_run_materials_finished_product_run_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_run_materials_finished_product_run_id_index ON public.finished_product_run_materials USING btree (finished_product_run_id);


--
-- Name: finished_product_run_materials_source_material_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_run_materials_source_material_id_index ON public.finished_product_run_materials USING btree (source_material_id);


--
-- Name: finished_product_runs_finished_product_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_runs_finished_product_id_index ON public.finished_product_runs USING btree (finished_product_id);


--
-- Name: finished_product_runs_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_runs_status_index ON public.finished_product_runs USING btree (status);


--
-- Name: finished_product_stock_movements_finished_product_id_moved_at_i; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_stock_movements_finished_product_id_moved_at_i ON public.finished_product_stock_movements USING btree (finished_product_id, moved_at);


--
-- Name: finished_product_stock_movements_finished_product_storage_id_mo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_stock_movements_finished_product_storage_id_mo ON public.finished_product_stock_movements USING btree (finished_product_storage_id, moved_at);


--
-- Name: finished_product_stock_movements_transfer_reference_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_stock_movements_transfer_reference_index ON public.finished_product_stock_movements USING btree (transfer_reference);


--
-- Name: finished_product_storage_slot_stocks_finished_product_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_storage_slot_stocks_finished_product_id_index ON public.finished_product_storage_slot_stocks USING btree (finished_product_id);


--
-- Name: finished_product_storage_slots_finished_product_storage_id_volu; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_product_storage_slots_finished_product_storage_id_volu ON public.finished_product_storage_slots USING btree (finished_product_storage_id, volume_litre);


--
-- Name: finished_products_finished_product_recipe_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_products_finished_product_recipe_id_index ON public.finished_products USING btree (finished_product_recipe_id);


--
-- Name: finished_products_is_active_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX finished_products_is_active_index ON public.finished_products USING btree (is_active);


--
-- Name: fp_stock_mov_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fp_stock_mov_order_idx ON public.finished_product_stock_movements USING btree (order_id);


--
-- Name: fp_stock_mov_run_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fp_stock_mov_run_idx ON public.finished_product_stock_movements USING btree (finished_product_run_id);


--
-- Name: fpr_pkg_run_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX fpr_pkg_run_idx ON public.finished_product_run_packagings USING btree (finished_product_run_id);


--
-- Name: jobs_queue_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX jobs_queue_index ON public.jobs USING btree (queue);


--
-- Name: materials_closed_by_batch_process_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX materials_closed_by_batch_process_id_index ON public.materials USING btree (closed_by_batch_process_id);


--
-- Name: materials_created_by_batch_process_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX materials_created_by_batch_process_id_index ON public.materials USING btree (created_by_batch_process_id);


--
-- Name: materials_current_location_type_current_location_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX materials_current_location_type_current_location_id_index ON public.materials USING btree (current_location_type, current_location_id);


--
-- Name: materials_origin_type_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX materials_origin_type_index ON public.materials USING btree (origin_type);


--
-- Name: materials_parent_material_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX materials_parent_material_id_index ON public.materials USING btree (parent_material_id);


--
-- Name: materials_production_batch_id_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX materials_production_batch_id_status_index ON public.materials USING btree (production_batch_id, status);


--
-- Name: materials_production_product_type_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX materials_production_product_type_id_index ON public.materials USING btree (production_product_type_id);


--
-- Name: materials_source_production_stage_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX materials_source_production_stage_id_index ON public.materials USING btree (source_production_stage_id);


--
-- Name: model_has_permissions_model_id_model_type_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX model_has_permissions_model_id_model_type_index ON public.model_has_permissions USING btree (model_id, model_type);


--
-- Name: model_has_roles_model_id_model_type_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX model_has_roles_model_id_model_type_index ON public.model_has_roles USING btree (model_id, model_type);


--
-- Name: order_status_histories_order_id_changed_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX order_status_histories_order_id_changed_at_index ON public.order_status_histories USING btree (order_id, changed_at);


--
-- Name: order_status_histories_to_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX order_status_histories_to_status_index ON public.order_status_histories USING btree (to_status);


--
-- Name: orders_customer_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX orders_customer_id_index ON public.orders USING btree (customer_id);


--
-- Name: orders_order_date_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX orders_order_date_index ON public.orders USING btree (order_date);


--
-- Name: orders_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX orders_status_index ON public.orders USING btree (status);


--
-- Name: personal_access_tokens_expires_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX personal_access_tokens_expires_at_index ON public.personal_access_tokens USING btree (expires_at);


--
-- Name: personal_access_tokens_tokenable_type_tokenable_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX personal_access_tokens_tokenable_type_tokenable_id_index ON public.personal_access_tokens USING btree (tokenable_type, tokenable_id);


--
-- Name: pkg_lot_mov_item_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pkg_lot_mov_item_idx ON public.packaging_lot_movements USING btree (packaging_item_id);


--
-- Name: pkg_lot_mov_lot_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pkg_lot_mov_lot_time_idx ON public.packaging_lot_movements USING btree (packaging_lot_id, moved_at);


--
-- Name: pkg_lot_mov_run_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pkg_lot_mov_run_idx ON public.packaging_lot_movements USING btree (finished_product_run_id);


--
-- Name: pkg_lot_mov_type_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pkg_lot_mov_type_idx ON public.packaging_lot_movements USING btree (movement_type);


--
-- Name: pkg_lots_item_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pkg_lots_item_status_idx ON public.packaging_lots USING btree (packaging_item_id, status);


--
-- Name: pkg_lots_received_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pkg_lots_received_idx ON public.packaging_lots USING btree (received_at);


--
-- Name: pkg_lots_supplier_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX pkg_lots_supplier_idx ON public.packaging_lots USING btree (product_supplier_id);


--
-- Name: prod_batch_input_batch_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX prod_batch_input_batch_idx ON public.production_batch_input_production_product_types USING btree (production_batch_id);


--
-- Name: prod_batch_input_type_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX prod_batch_input_type_idx ON public.production_batch_input_production_product_types USING btree (production_product_type_id);


--
-- Name: prod_batch_output_batch_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX prod_batch_output_batch_idx ON public.production_batch_output_production_product_types USING btree (production_batch_id);


--
-- Name: prod_batch_output_type_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX prod_batch_output_type_idx ON public.production_batch_output_production_product_types USING btree (production_product_type_id);


--
-- Name: product_suppliers_is_active_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX product_suppliers_is_active_index ON public.product_suppliers USING btree (is_active);


--
-- Name: production_batches_created_by_user_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX production_batches_created_by_user_id_index ON public.production_batches USING btree (created_by_user_id);


--
-- Name: production_batches_production_line_id_status_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX production_batches_production_line_id_status_index ON public.production_batches USING btree (production_line_id, status);


--
-- Name: sessions_last_activity_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sessions_last_activity_index ON public.sessions USING btree (last_activity);


--
-- Name: sessions_user_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sessions_user_id_index ON public.sessions USING btree (user_id);


--
-- Name: storage_movements_batch_process_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX storage_movements_batch_process_id_index ON public.storage_movements USING btree (batch_process_id);


--
-- Name: storage_movements_material_id_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX storage_movements_material_id_index ON public.storage_movements USING btree (material_id);


--
-- Name: storage_movements_moved_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX storage_movements_moved_at_index ON public.storage_movements USING btree (moved_at);


--
-- Name: storage_movements_source_type_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX storage_movements_source_type_index ON public.storage_movements USING btree (source_type);


--
-- Name: storage_movements_storage_unit_id_movement_type_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX storage_movements_storage_unit_id_movement_type_index ON public.storage_movements USING btree (storage_unit_id, movement_type);


--
-- Name: storage_units_allow_multiple_production_product_types_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX storage_units_allow_multiple_production_product_types_index ON public.storage_units USING btree (allow_multiple_production_product_types);


--
-- Name: storage_units_allow_multiple_production_products_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX storage_units_allow_multiple_production_products_index ON public.storage_units USING btree (allow_multiple_production_products);


--
-- Name: storage_units_is_active_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX storage_units_is_active_index ON public.storage_units USING btree (is_active);


--
-- Name: storage_units_is_blend_tank_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX storage_units_is_blend_tank_index ON public.storage_units USING btree (is_blend_tank);


--
-- Name: suppliers_is_active_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX suppliers_is_active_index ON public.suppliers USING btree (is_active);


--
-- Name: user_notif_subject_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_notif_subject_idx ON public.user_notifications USING btree (subject_type, subject_id, created_at);


--
-- Name: user_notifications_user_id_created_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_notifications_user_id_created_at_index ON public.user_notifications USING btree (user_id, created_at);


--
-- Name: user_notifications_user_id_read_at_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_notifications_user_id_read_at_index ON public.user_notifications USING btree (user_id, read_at);


--
-- Name: analytics_reports analytics_reports_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.analytics_reports
    ADD CONSTRAINT analytics_reports_user_id_foreign FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: batch_process_field_values batch_process_field_values_batch_process_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_field_values
    ADD CONSTRAINT batch_process_field_values_batch_process_id_foreign FOREIGN KEY (batch_process_id) REFERENCES public.batch_processes(id) ON DELETE CASCADE;


--
-- Name: batch_process_field_values batch_process_field_values_process_type_field_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_field_values
    ADD CONSTRAINT batch_process_field_values_process_type_field_id_foreign FOREIGN KEY (process_type_field_id) REFERENCES public.process_type_fields(id) ON DELETE CASCADE;


--
-- Name: batch_process_reviews batch_process_reviews_batch_process_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_reviews
    ADD CONSTRAINT batch_process_reviews_batch_process_id_foreign FOREIGN KEY (batch_process_id) REFERENCES public.batch_processes(id) ON DELETE CASCADE;


--
-- Name: batch_process_reviews batch_process_reviews_reviewed_by_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_reviews
    ADD CONSTRAINT batch_process_reviews_reviewed_by_user_id_foreign FOREIGN KEY (reviewed_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: batch_process_reviews batch_process_reviews_submitted_by_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_process_reviews
    ADD CONSTRAINT batch_process_reviews_submitted_by_user_id_foreign FOREIGN KEY (submitted_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: batch_processes batch_processes_approved_by_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_processes
    ADD CONSTRAINT batch_processes_approved_by_user_id_foreign FOREIGN KEY (approved_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: batch_processes batch_processes_operator_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_processes
    ADD CONSTRAINT batch_processes_operator_id_foreign FOREIGN KEY (operator_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: batch_processes batch_processes_production_batch_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_processes
    ADD CONSTRAINT batch_processes_production_batch_id_foreign FOREIGN KEY (production_batch_id) REFERENCES public.production_batches(id) ON DELETE CASCADE;


--
-- Name: batch_processes batch_processes_production_stage_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_processes
    ADD CONSTRAINT batch_processes_production_stage_id_foreign FOREIGN KEY (production_stage_id) REFERENCES public.production_stages(id) ON DELETE RESTRICT;


--
-- Name: batch_processes batch_processes_submitted_by_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.batch_processes
    ADD CONSTRAINT batch_processes_submitted_by_user_id_foreign FOREIGN KEY (submitted_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: customer_addresses customer_addresses_customer_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_addresses
    ADD CONSTRAINT customer_addresses_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: customer_preferred_recipes customer_preferred_recipes_customer_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_preferred_recipes
    ADD CONSTRAINT customer_preferred_recipes_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE CASCADE;


--
-- Name: customer_preferred_recipes customer_preferred_recipes_finished_product_recipe_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_preferred_recipes
    ADD CONSTRAINT customer_preferred_recipes_finished_product_recipe_id_foreign FOREIGN KEY (finished_product_recipe_id) REFERENCES public.finished_product_recipes(id) ON DELETE CASCADE;


--
-- Name: finished_product_components finished_product_components_finished_product_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_components
    ADD CONSTRAINT finished_product_components_finished_product_id_foreign FOREIGN KEY (finished_product_id) REFERENCES public.finished_products(id) ON DELETE CASCADE;


--
-- Name: finished_product_components finished_product_components_packaging_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_components
    ADD CONSTRAINT finished_product_components_packaging_item_id_foreign FOREIGN KEY (packaging_item_id) REFERENCES public.packaging_items(id) ON DELETE RESTRICT;


--
-- Name: finished_product_recipe_attribute_rules finished_product_recipe_attribute_rules_attribute_definition_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_attribute_rules
    ADD CONSTRAINT finished_product_recipe_attribute_rules_attribute_definition_id FOREIGN KEY (attribute_definition_id) REFERENCES public.attribute_definitions(id) ON DELETE RESTRICT;


--
-- Name: finished_product_recipe_attribute_rules finished_product_recipe_attribute_rules_finished_product_recipe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_attribute_rules
    ADD CONSTRAINT finished_product_recipe_attribute_rules_finished_product_recipe FOREIGN KEY (finished_product_recipe_id) REFERENCES public.finished_product_recipes(id) ON DELETE CASCADE;


--
-- Name: finished_product_recipe_contents finished_product_recipe_contents_finished_product_recipe_id_for; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_contents
    ADD CONSTRAINT finished_product_recipe_contents_finished_product_recipe_id_for FOREIGN KEY (finished_product_recipe_id) REFERENCES public.finished_product_recipes(id) ON DELETE CASCADE;


--
-- Name: finished_product_recipe_contents finished_product_recipe_contents_production_product_type_id_for; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_recipe_contents
    ADD CONSTRAINT finished_product_recipe_contents_production_product_type_id_for FOREIGN KEY (production_product_type_id) REFERENCES public.production_product_types(id) ON DELETE RESTRICT;


--
-- Name: finished_product_run_attribute_values finished_product_run_attribute_values_attribute_definition_id_f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_attribute_values
    ADD CONSTRAINT finished_product_run_attribute_values_attribute_definition_id_f FOREIGN KEY (attribute_definition_id) REFERENCES public.attribute_definitions(id) ON DELETE RESTRICT;


--
-- Name: finished_product_run_attribute_values finished_product_run_attribute_values_finished_product_run_id_f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_attribute_values
    ADD CONSTRAINT finished_product_run_attribute_values_finished_product_run_id_f FOREIGN KEY (finished_product_run_id) REFERENCES public.finished_product_runs(id) ON DELETE CASCADE;


--
-- Name: finished_product_run_materials finished_product_run_materials_finished_product_run_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_materials
    ADD CONSTRAINT finished_product_run_materials_finished_product_run_id_foreign FOREIGN KEY (finished_product_run_id) REFERENCES public.finished_product_runs(id) ON DELETE CASCADE;


--
-- Name: finished_product_run_materials finished_product_run_materials_source_material_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_materials
    ADD CONSTRAINT finished_product_run_materials_source_material_id_foreign FOREIGN KEY (source_material_id) REFERENCES public.materials(id) ON DELETE RESTRICT;


--
-- Name: finished_product_run_packagings finished_product_run_packagings_packaging_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_packagings
    ADD CONSTRAINT finished_product_run_packagings_packaging_item_id_foreign FOREIGN KEY (packaging_item_id) REFERENCES public.packaging_items(id) ON DELETE RESTRICT;


--
-- Name: finished_product_run_packagings finished_product_run_packagings_packaging_lot_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_packagings
    ADD CONSTRAINT finished_product_run_packagings_packaging_lot_id_foreign FOREIGN KEY (packaging_lot_id) REFERENCES public.packaging_lots(id) ON DELETE RESTRICT;


--
-- Name: finished_product_runs finished_product_runs_finished_product_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_runs
    ADD CONSTRAINT finished_product_runs_finished_product_id_foreign FOREIGN KEY (finished_product_id) REFERENCES public.finished_products(id) ON DELETE RESTRICT;


--
-- Name: finished_product_runs finished_product_runs_finished_product_recipe_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_runs
    ADD CONSTRAINT finished_product_runs_finished_product_recipe_id_foreign FOREIGN KEY (finished_product_recipe_id) REFERENCES public.finished_product_recipes(id) ON DELETE RESTRICT;


--
-- Name: finished_product_runs finished_product_runs_source_storage_unit_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_runs
    ADD CONSTRAINT finished_product_runs_source_storage_unit_id_foreign FOREIGN KEY (source_storage_unit_id) REFERENCES public.storage_units(id) ON DELETE SET NULL;


--
-- Name: finished_product_stock_movements finished_product_stock_movements_counterparty_slot_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_counterparty_slot_id_foreign FOREIGN KEY (counterparty_slot_id) REFERENCES public.finished_product_storage_slots(id) ON DELETE SET NULL;


--
-- Name: finished_product_stock_movements finished_product_stock_movements_created_by_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_created_by_user_id_foreign FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: finished_product_stock_movements finished_product_stock_movements_finished_product_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_finished_product_id_foreign FOREIGN KEY (finished_product_id) REFERENCES public.finished_products(id) ON DELETE RESTRICT;


--
-- Name: finished_product_stock_movements finished_product_stock_movements_finished_product_run_id_foreig; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_finished_product_run_id_foreig FOREIGN KEY (finished_product_run_id) REFERENCES public.finished_product_runs(id) ON DELETE SET NULL;


--
-- Name: finished_product_stock_movements finished_product_stock_movements_finished_product_storage_id_fo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_finished_product_storage_id_fo FOREIGN KEY (finished_product_storage_id) REFERENCES public.finished_product_storages(id) ON DELETE RESTRICT;


--
-- Name: finished_product_stock_movements finished_product_stock_movements_finished_product_storage_slot_; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_finished_product_storage_slot_ FOREIGN KEY (finished_product_storage_slot_id) REFERENCES public.finished_product_storage_slots(id) ON DELETE RESTRICT;


--
-- Name: finished_product_stock_movements finished_product_stock_movements_order_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_stock_movements
    ADD CONSTRAINT finished_product_stock_movements_order_id_foreign FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE SET NULL;


--
-- Name: finished_product_storage_slot_stocks finished_product_storage_slot_stocks_finished_product_id_foreig; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_slot_stocks
    ADD CONSTRAINT finished_product_storage_slot_stocks_finished_product_id_foreig FOREIGN KEY (finished_product_id) REFERENCES public.finished_products(id) ON DELETE RESTRICT;


--
-- Name: finished_product_storage_slot_stocks finished_product_storage_slot_stocks_finished_product_storage_s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_slot_stocks
    ADD CONSTRAINT finished_product_storage_slot_stocks_finished_product_storage_s FOREIGN KEY (finished_product_storage_slot_id) REFERENCES public.finished_product_storage_slots(id) ON DELETE CASCADE;


--
-- Name: finished_product_storage_slots finished_product_storage_slots_finished_product_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_slots
    ADD CONSTRAINT finished_product_storage_slots_finished_product_id_foreign FOREIGN KEY (finished_product_id) REFERENCES public.finished_products(id) ON DELETE SET NULL;


--
-- Name: finished_product_storage_slots finished_product_storage_slots_finished_product_storage_id_fore; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_slots
    ADD CONSTRAINT finished_product_storage_slots_finished_product_storage_id_fore FOREIGN KEY (finished_product_storage_id) REFERENCES public.finished_product_storages(id) ON DELETE CASCADE;


--
-- Name: finished_product_storage_stocks finished_product_storage_stocks_finished_product_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_stocks
    ADD CONSTRAINT finished_product_storage_stocks_finished_product_id_foreign FOREIGN KEY (finished_product_id) REFERENCES public.finished_products(id) ON DELETE RESTRICT;


--
-- Name: finished_product_storage_stocks finished_product_storage_stocks_finished_product_storage_id_for; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_storage_stocks
    ADD CONSTRAINT finished_product_storage_stocks_finished_product_storage_id_for FOREIGN KEY (finished_product_storage_id) REFERENCES public.finished_product_storages(id) ON DELETE CASCADE;


--
-- Name: finished_products finished_products_finished_product_recipe_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_products
    ADD CONSTRAINT finished_products_finished_product_recipe_id_foreign FOREIGN KEY (finished_product_recipe_id) REFERENCES public.finished_product_recipes(id) ON DELETE RESTRICT;


--
-- Name: finished_product_run_packagings fpr_pkg_run_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finished_product_run_packagings
    ADD CONSTRAINT fpr_pkg_run_fk FOREIGN KEY (finished_product_run_id) REFERENCES public.finished_product_runs(id) ON DELETE CASCADE;


--
-- Name: material_attribute_values material_attribute_values_material_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_attribute_values
    ADD CONSTRAINT material_attribute_values_material_id_foreign FOREIGN KEY (material_id) REFERENCES public.materials(id) ON DELETE CASCADE;


--
-- Name: material_attribute_values material_attribute_values_production_product_attribute_definiti; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.material_attribute_values
    ADD CONSTRAINT material_attribute_values_production_product_attribute_definiti FOREIGN KEY (production_product_attribute_definition_id) REFERENCES public.production_product_attribute_definitions(id) ON DELETE RESTRICT;


--
-- Name: materials materials_closed_by_batch_process_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_closed_by_batch_process_id_foreign FOREIGN KEY (closed_by_batch_process_id) REFERENCES public.batch_processes(id) ON DELETE SET NULL;


--
-- Name: materials materials_created_by_batch_process_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_created_by_batch_process_id_foreign FOREIGN KEY (created_by_batch_process_id) REFERENCES public.batch_processes(id) ON DELETE SET NULL;


--
-- Name: materials materials_parent_material_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_parent_material_id_foreign FOREIGN KEY (parent_material_id) REFERENCES public.materials(id) ON DELETE SET NULL;


--
-- Name: materials materials_production_batch_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_production_batch_id_foreign FOREIGN KEY (production_batch_id) REFERENCES public.production_batches(id) ON DELETE SET NULL;


--
-- Name: materials materials_production_product_type_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_production_product_type_id_foreign FOREIGN KEY (production_product_type_id) REFERENCES public.production_product_types(id) ON DELETE RESTRICT;


--
-- Name: materials materials_source_production_stage_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.materials
    ADD CONSTRAINT materials_source_production_stage_id_foreign FOREIGN KEY (source_production_stage_id) REFERENCES public.production_stages(id) ON DELETE SET NULL;


--
-- Name: model_has_permissions model_has_permissions_permission_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.model_has_permissions
    ADD CONSTRAINT model_has_permissions_permission_id_foreign FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE;


--
-- Name: model_has_roles model_has_roles_role_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.model_has_roles
    ADD CONSTRAINT model_has_roles_role_id_foreign FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: order_items order_items_finished_product_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_finished_product_id_foreign FOREIGN KEY (finished_product_id) REFERENCES public.finished_products(id) ON DELETE RESTRICT;


--
-- Name: order_items order_items_order_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_order_id_foreign FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: order_status_histories order_status_histories_changed_by_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_histories
    ADD CONSTRAINT order_status_histories_changed_by_user_id_foreign FOREIGN KEY (changed_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: order_status_histories order_status_histories_order_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_histories
    ADD CONSTRAINT order_status_histories_order_id_foreign FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: orders orders_created_by_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_created_by_user_id_foreign FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: orders orders_customer_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: orders orders_delivery_address_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_delivery_address_id_foreign FOREIGN KEY (delivery_address_id) REFERENCES public.customer_addresses(id) ON DELETE RESTRICT;


--
-- Name: packaging_lot_movements packaging_lot_movements_finished_product_run_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_finished_product_run_id_foreign FOREIGN KEY (finished_product_run_id) REFERENCES public.finished_product_runs(id) ON DELETE SET NULL;


--
-- Name: packaging_lot_movements packaging_lot_movements_packaging_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_packaging_item_id_foreign FOREIGN KEY (packaging_item_id) REFERENCES public.packaging_items(id) ON DELETE RESTRICT;


--
-- Name: packaging_lot_movements packaging_lot_movements_packaging_lot_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_packaging_lot_id_foreign FOREIGN KEY (packaging_lot_id) REFERENCES public.packaging_lots(id) ON DELETE RESTRICT;


--
-- Name: packaging_lot_movements packaging_lot_movements_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_user_id_foreign FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: packaging_lots packaging_lots_packaging_item_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lots
    ADD CONSTRAINT packaging_lots_packaging_item_id_foreign FOREIGN KEY (packaging_item_id) REFERENCES public.packaging_items(id) ON DELETE RESTRICT;


--
-- Name: packaging_lots packaging_lots_product_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.packaging_lots
    ADD CONSTRAINT packaging_lots_product_supplier_id_foreign FOREIGN KEY (product_supplier_id) REFERENCES public.product_suppliers(id) ON DELETE SET NULL;


--
-- Name: process_type_fields process_type_fields_process_type_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.process_type_fields
    ADD CONSTRAINT process_type_fields_process_type_id_foreign FOREIGN KEY (process_type_id) REFERENCES public.process_types(id) ON DELETE CASCADE;


--
-- Name: production_batch_input_production_product_types prod_batch_input_batch_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batch_input_production_product_types
    ADD CONSTRAINT prod_batch_input_batch_fk FOREIGN KEY (production_batch_id) REFERENCES public.production_batches(id) ON DELETE CASCADE;


--
-- Name: production_batch_input_production_product_types prod_batch_input_type_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batch_input_production_product_types
    ADD CONSTRAINT prod_batch_input_type_fk FOREIGN KEY (production_product_type_id) REFERENCES public.production_product_types(id) ON DELETE RESTRICT;


--
-- Name: production_batch_output_production_product_types prod_batch_output_batch_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batch_output_production_product_types
    ADD CONSTRAINT prod_batch_output_batch_fk FOREIGN KEY (production_batch_id) REFERENCES public.production_batches(id) ON DELETE CASCADE;


--
-- Name: production_batch_output_production_product_types prod_batch_output_type_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batch_output_production_product_types
    ADD CONSTRAINT prod_batch_output_type_fk FOREIGN KEY (production_product_type_id) REFERENCES public.production_product_types(id) ON DELETE RESTRICT;


--
-- Name: production_line_input_production_products prod_line_input_product_type_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_input_production_products
    ADD CONSTRAINT prod_line_input_product_type_fk FOREIGN KEY (production_product_type_id) REFERENCES public.production_product_types(id) ON DELETE RESTRICT;


--
-- Name: production_line_output_production_products prod_line_output_product_type_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_output_production_products
    ADD CONSTRAINT prod_line_output_product_type_fk FOREIGN KEY (production_product_type_id) REFERENCES public.production_product_types(id) ON DELETE RESTRICT;


--
-- Name: production_stage_extension_input_production_products prod_stage_ext_input_ext_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_input_production_products
    ADD CONSTRAINT prod_stage_ext_input_ext_fk FOREIGN KEY (production_stage_extension_id) REFERENCES public.production_stage_extensions(id) ON DELETE CASCADE;


--
-- Name: production_stage_extension_input_production_products prod_stage_ext_input_product_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_input_production_products
    ADD CONSTRAINT prod_stage_ext_input_product_fk FOREIGN KEY (production_product_id) REFERENCES public.production_products(id) ON DELETE RESTRICT;


--
-- Name: production_stage_extension_output_production_products prod_stage_ext_output_ext_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_output_production_products
    ADD CONSTRAINT prod_stage_ext_output_ext_fk FOREIGN KEY (production_stage_extension_id) REFERENCES public.production_stage_extensions(id) ON DELETE CASCADE;


--
-- Name: production_stage_extension_output_production_products prod_stage_ext_output_product_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_output_production_products
    ADD CONSTRAINT prod_stage_ext_output_product_fk FOREIGN KEY (production_product_id) REFERENCES public.production_products(id) ON DELETE RESTRICT;


--
-- Name: production_batches production_batches_created_by_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT production_batches_created_by_user_id_foreign FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: production_batches production_batches_production_line_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT production_batches_production_line_id_foreign FOREIGN KEY (production_line_id) REFERENCES public.production_lines(id) ON DELETE RESTRICT;


--
-- Name: production_batches production_batches_supplier_field_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT production_batches_supplier_field_id_foreign FOREIGN KEY (supplier_field_id) REFERENCES public.supplier_fields(id) ON DELETE SET NULL;


--
-- Name: production_batches production_batches_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT production_batches_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;


--
-- Name: production_line_input_production_products production_line_input_production_products_production_line_id_fo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_input_production_products
    ADD CONSTRAINT production_line_input_production_products_production_line_id_fo FOREIGN KEY (production_line_id) REFERENCES public.production_lines(id) ON DELETE CASCADE;


--
-- Name: production_line_input_production_products production_line_input_production_products_production_product_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_input_production_products
    ADD CONSTRAINT production_line_input_production_products_production_product_id FOREIGN KEY (production_product_id) REFERENCES public.production_products(id) ON DELETE RESTRICT;


--
-- Name: production_line_output_production_products production_line_output_production_products_production_line_id_f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_output_production_products
    ADD CONSTRAINT production_line_output_production_products_production_line_id_f FOREIGN KEY (production_line_id) REFERENCES public.production_lines(id) ON DELETE CASCADE;


--
-- Name: production_line_output_production_products production_line_output_production_products_production_product_i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_line_output_production_products
    ADD CONSTRAINT production_line_output_production_products_production_product_i FOREIGN KEY (production_product_id) REFERENCES public.production_products(id) ON DELETE RESTRICT;


--
-- Name: production_product_attribute_definitions production_product_attribute_definitions_attribute_definition_i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_product_attribute_definitions
    ADD CONSTRAINT production_product_attribute_definitions_attribute_definition_i FOREIGN KEY (attribute_definition_id) REFERENCES public.attribute_definitions(id) ON DELETE CASCADE;


--
-- Name: production_product_attribute_definitions production_product_attribute_definitions_production_product_id_; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_product_attribute_definitions
    ADD CONSTRAINT production_product_attribute_definitions_production_product_id_ FOREIGN KEY (production_product_id) REFERENCES public.production_products(id) ON DELETE CASCADE;


--
-- Name: production_product_types production_product_types_production_product_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_product_types
    ADD CONSTRAINT production_product_types_production_product_id_foreign FOREIGN KEY (production_product_id) REFERENCES public.production_products(id) ON DELETE RESTRICT;


--
-- Name: production_stage_extension_values production_stage_extension_values_production_stage_extension_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_values
    ADD CONSTRAINT production_stage_extension_values_production_stage_extension_id FOREIGN KEY (production_stage_extension_id) REFERENCES public.production_stage_extensions(id) ON DELETE CASCADE;


--
-- Name: production_stage_extension_values production_stage_extension_values_stage_extension_type_field_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extension_values
    ADD CONSTRAINT production_stage_extension_values_stage_extension_type_field_id FOREIGN KEY (stage_extension_type_field_id) REFERENCES public.stage_extension_type_fields(id) ON DELETE RESTRICT;


--
-- Name: production_stage_extensions production_stage_extensions_production_stage_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extensions
    ADD CONSTRAINT production_stage_extensions_production_stage_id_foreign FOREIGN KEY (production_stage_id) REFERENCES public.production_stages(id) ON DELETE CASCADE;


--
-- Name: production_stage_extensions production_stage_extensions_stage_extension_type_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stage_extensions
    ADD CONSTRAINT production_stage_extensions_stage_extension_type_id_foreign FOREIGN KEY (stage_extension_type_id) REFERENCES public.stage_extension_types(id) ON DELETE RESTRICT;


--
-- Name: production_stages production_stages_process_type_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stages
    ADD CONSTRAINT production_stages_process_type_id_foreign FOREIGN KEY (process_type_id) REFERENCES public.process_types(id) ON DELETE RESTRICT;


--
-- Name: production_stages production_stages_production_line_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_stages
    ADD CONSTRAINT production_stages_production_line_id_foreign FOREIGN KEY (production_line_id) REFERENCES public.production_lines(id) ON DELETE CASCADE;


--
-- Name: role_has_permissions role_has_permissions_permission_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_has_permissions
    ADD CONSTRAINT role_has_permissions_permission_id_foreign FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE;


--
-- Name: role_has_permissions role_has_permissions_role_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_has_permissions
    ADD CONSTRAINT role_has_permissions_role_id_foreign FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: stage_extension_type_fields stage_extension_type_fields_stage_extension_type_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stage_extension_type_fields
    ADD CONSTRAINT stage_extension_type_fields_stage_extension_type_id_foreign FOREIGN KEY (stage_extension_type_id) REFERENCES public.stage_extension_types(id) ON DELETE CASCADE;


--
-- Name: storage_movements storage_movements_batch_process_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_movements
    ADD CONSTRAINT storage_movements_batch_process_id_foreign FOREIGN KEY (batch_process_id) REFERENCES public.batch_processes(id) ON DELETE SET NULL;


--
-- Name: storage_movements storage_movements_counterparty_storage_unit_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_movements
    ADD CONSTRAINT storage_movements_counterparty_storage_unit_id_foreign FOREIGN KEY (counterparty_storage_unit_id) REFERENCES public.storage_units(id) ON DELETE SET NULL;


--
-- Name: storage_movements storage_movements_finished_product_run_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_movements
    ADD CONSTRAINT storage_movements_finished_product_run_id_foreign FOREIGN KEY (finished_product_run_id) REFERENCES public.finished_product_runs(id) ON DELETE SET NULL;


--
-- Name: storage_movements storage_movements_material_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_movements
    ADD CONSTRAINT storage_movements_material_id_foreign FOREIGN KEY (material_id) REFERENCES public.materials(id) ON DELETE RESTRICT;


--
-- Name: storage_movements storage_movements_storage_unit_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_movements
    ADD CONSTRAINT storage_movements_storage_unit_id_foreign FOREIGN KEY (storage_unit_id) REFERENCES public.storage_units(id) ON DELETE RESTRICT;


--
-- Name: storage_unit_allowed_production_product_types storage_unit_allowed_production_product_types_production_produc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_product_types
    ADD CONSTRAINT storage_unit_allowed_production_product_types_production_produc FOREIGN KEY (production_product_type_id) REFERENCES public.production_product_types(id) ON DELETE RESTRICT;


--
-- Name: storage_unit_allowed_production_product_types storage_unit_allowed_production_product_types_storage_unit_id_f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_product_types
    ADD CONSTRAINT storage_unit_allowed_production_product_types_storage_unit_id_f FOREIGN KEY (storage_unit_id) REFERENCES public.storage_units(id) ON DELETE CASCADE;


--
-- Name: storage_unit_allowed_production_products storage_unit_allowed_production_products_production_product_id_; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_products
    ADD CONSTRAINT storage_unit_allowed_production_products_production_product_id_ FOREIGN KEY (production_product_id) REFERENCES public.production_products(id) ON DELETE RESTRICT;


--
-- Name: storage_unit_allowed_production_products storage_unit_allowed_production_products_storage_unit_id_foreig; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_allowed_production_products
    ADD CONSTRAINT storage_unit_allowed_production_products_storage_unit_id_foreig FOREIGN KEY (storage_unit_id) REFERENCES public.storage_units(id) ON DELETE CASCADE;


--
-- Name: storage_unit_attribute_rules storage_unit_attribute_rules_production_product_attribute_defin; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_attribute_rules
    ADD CONSTRAINT storage_unit_attribute_rules_production_product_attribute_defin FOREIGN KEY (production_product_attribute_definition_id) REFERENCES public.production_product_attribute_definitions(id) ON DELETE RESTRICT;


--
-- Name: storage_unit_attribute_rules storage_unit_attribute_rules_storage_unit_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_attribute_rules
    ADD CONSTRAINT storage_unit_attribute_rules_storage_unit_id_foreign FOREIGN KEY (storage_unit_id) REFERENCES public.storage_units(id) ON DELETE CASCADE;


--
-- Name: storage_unit_balances storage_unit_balances_production_product_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_balances
    ADD CONSTRAINT storage_unit_balances_production_product_id_foreign FOREIGN KEY (production_product_id) REFERENCES public.production_products(id) ON DELETE RESTRICT;


--
-- Name: storage_unit_balances storage_unit_balances_storage_unit_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_unit_balances
    ADD CONSTRAINT storage_unit_balances_storage_unit_id_foreign FOREIGN KEY (storage_unit_id) REFERENCES public.storage_units(id) ON DELETE CASCADE;


--
-- Name: supplier_fields supplier_fields_supplier_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_fields
    ADD CONSTRAINT supplier_fields_supplier_id_foreign FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE CASCADE;


--
-- Name: user_notifications user_notifications_sent_by_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notifications
    ADD CONSTRAINT user_notifications_sent_by_user_id_foreign FOREIGN KEY (sent_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: user_notifications user_notifications_user_id_foreign; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_notifications
    ADD CONSTRAINT user_notifications_user_id_foreign FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict rOAscTtHB582Ryc8nyencAUUU6h1nFQUsW1DQfIi5ZcuV460aPMIlcwGMiD764i

