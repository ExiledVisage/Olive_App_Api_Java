-- stage_extension_type_fields: uzanti tiplerinin ayar alanlari

CREATE TABLE stage_extension_type_fields (
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
    default_value_json jsonb,
    options_json jsonb,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE stage_extension_type_fields_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE stage_extension_type_fields_id_seq OWNED BY stage_extension_type_fields.id;

ALTER TABLE ONLY stage_extension_type_fields ALTER COLUMN id SET DEFAULT nextval('stage_extension_type_fields_id_seq'::regclass);

ALTER TABLE ONLY stage_extension_type_fields
    ADD CONSTRAINT stage_ext_type_field_unique UNIQUE (stage_extension_type_id, field_key);

ALTER TABLE ONLY stage_extension_type_fields
    ADD CONSTRAINT stage_extension_type_fields_pkey PRIMARY KEY (id);

ALTER TABLE ONLY stage_extension_type_fields
    ADD CONSTRAINT stage_extension_type_fields_stage_extension_type_id_foreign FOREIGN KEY (stage_extension_type_id)
        REFERENCES stage_extension_types(id) ON DELETE CASCADE;

-- Kodda tanimli referans satirlari

insert into stage_extension_type_fields (id, stage_extension_type_id, field_key, label, data_type, is_required, sort_order, default_value_boolean, default_value_integer, default_value_decimal, default_value_text, default_value_json, options_json, description) values
	(1, 2, 'action', 'Hareket turu', 'text', false, 1, null, null, null, NULL, NULL, '["inbound", "outbound", "transfer"]', 'Doldurulursa isci bu asamada hareket turunu secemez; her zaman bu tur uygulanir. Bos birakilirsa isci secer.'),
	(2, 3, 'remainder_product', 'Kalan cikti', 'text', false, 1, null, null, null, NULL, NULL, NULL, 'Cikti urununun anahtari (ornek: pomace). Bu cikti girdi eksi ayni birimdeki diger ciktilar olarak hesaplanir; elle degistirilirse hesaplanan deger ve sapma orani kaydedilir.'),
	(3, 8, 'machine_types', 'Makine sirasi', 'json', true, 1, null, null, null, NULL, NULL, NULL, 'Makine tipi anahtarlari gecis sirasinda: ["kirici", "malaksor", "dekantor", "separator"].');
