-- permissions: izinler; adlari kodda (@PreAuthorize) gecer

CREATE TABLE permissions (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE permissions_id_seq OWNED BY permissions.id;

ALTER TABLE ONLY permissions ALTER COLUMN id SET DEFAULT nextval('permissions_id_seq'::regclass);

ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_name_unique UNIQUE (name);

ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);

-- Kodda tanimli referans satirlari

insert into permissions (id, name) values
	(1, 'supplier.view'),
	(2, 'supplier.manage'),
	(3, 'analytics.view'),
	(4, 'product_supplier.view'),
	(5, 'product_supplier.manage'),
	(6, 'packaging_lot.view'),
	(7, 'packaging_lot.receive'),
	(8, 'packaging_lot.adjustment'),
	(9, 'production_product_type.view'),
	(10, 'production_product_type.manage'),
	(11, 'production_line.view'),
	(12, 'production_line.manage'),
	(13, 'production_stage.manage'),
	(14, 'process_type.manage'),
	(15, 'process_type_field.manage'),
	(16, 'attribute_definition.view'),
	(17, 'attribute_definition.manage'),
	(18, 'packaging_item.view'),
	(19, 'packaging_item.manage'),
	(20, 'production_batch.view'),
	(21, 'production_batch.create'),
	(22, 'production_batch.update'),
	(23, 'production_batch.delete'),
	(24, 'batch_process.view'),
	(25, 'batch_process.fill'),
	(26, 'batch_process.complete'),
	(27, 'batch_process.skip'),
	(28, 'batch_process.reopen'),
	(29, 'batch_process.approve'),
	(30, 'calendar.view'),
	(31, 'notification.send'),
	(32, 'storage_unit.view'),
	(33, 'storage_unit.manage'),
	(34, 'storage_movement.view'),
	(35, 'storage_movement.create'),
	(36, 'storage_movement.transfer'),
	(37, 'storage_movement.adjustment'),
	(38, 'finished_product_recipe.view'),
	(39, 'finished_product_recipe.manage'),
	(40, 'finished_product.view'),
	(41, 'finished_product.manage'),
	(42, 'finished_product_run.view'),
	(43, 'finished_product_run.create'),
	(44, 'finished_product_run.complete'),
	(45, 'finished_product_storage.view'),
	(46, 'finished_product_storage.manage'),
	(47, 'finished_product_storage.stock_in'),
	(48, 'finished_product_storage.stock_out'),
	(49, 'customer.view'),
	(50, 'customer.manage'),
	(51, 'order.view'),
	(52, 'order.create'),
	(53, 'order.update'),
	(54, 'order.update_status'),
	(55, 'storage_unit.measure'),
	(56, 'machine.view'),
	(57, 'machine.manage'),
	(58, 'production_batch.price');
