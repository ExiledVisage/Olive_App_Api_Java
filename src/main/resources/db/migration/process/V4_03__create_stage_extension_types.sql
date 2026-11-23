-- stage_extension_types: asama uzanti tipleri; anahtarlari kodda islenir

CREATE TABLE stage_extension_types (
    id bigint NOT NULL,
    key character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE stage_extension_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE stage_extension_types_id_seq OWNED BY stage_extension_types.id;

ALTER TABLE ONLY stage_extension_types ALTER COLUMN id SET DEFAULT nextval('stage_extension_types_id_seq'::regclass);

ALTER TABLE ONLY stage_extension_types
    ADD CONSTRAINT stage_extension_types_key_unique UNIQUE (key);

ALTER TABLE ONLY stage_extension_types
    ADD CONSTRAINT stage_extension_types_pkey PRIMARY KEY (id);

-- Kodda tanimli referans satirlari

insert into stage_extension_types (id, key, name, description) values
	(1, 'weighing', 'Weighing', 'Bu asamada secilen urun tipi tartilir.'),
	(2, 'storage_movement', 'Storage Movement', 'Bu asamada secilen urun tipi storage unit tarafina tasinabilir.'),
	(3, 'material_transformation', 'Material Transformation', 'Bu asamada input urun tiplerinden output urun tipleri olusur.'),
	(4, 'analysis', 'Analysis', 'Bu asamada secilen urun tipi icin analiz islemleri yapilir.'),
	(5, 'pre_settling', 'Pre-settling', 'Bu asamada urun on dinlendirme tankina alinir; asama tank bosaltilinca tamamlanir, fark tortu olur.'),
	(6, 'timer', 'Timer', 'Bu asamada makine suresi kronometreyle olculur; makine bitirilmeden asama tamamlanmaz.'),
	(7, 'yield', 'Yield', 'Bu asamada randiman hesaplanir: on dinlendirmeden son tanka giden yag / sikima giren zeytin x 100 (kg); elle degistirilirse hesaplanan deger ve sapma orani kaydedilir.'),
	(8, 'machine_route', 'Machine Route', 'Bu asamada parti sirayla makinelerden gecer (Kirici, Malaksor...); her makine icin sure ve olcumler girilen bir adim acilir. Kronometre baslamadan adim baslamaz, acik adim varken kronometre bitmez.');
