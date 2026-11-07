-- Laravel: 2026_09_21_120000_add_production_product_type_to_production_line_products.php

alter table "production_line_input_production_products"
    add column "production_product_type_id" bigint null;

alter table "production_line_input_production_products"
    add constraint "prod_line_input_product_type_fk"
        foreign key ("production_product_type_id")
        references "production_product_types" ("id")
        on delete restrict;

alter table "production_line_output_production_products"
    add column "production_product_type_id" bigint null;

alter table "production_line_output_production_products"
    add constraint "prod_line_output_product_type_fk"
        foreign key ("production_product_type_id")
        references "production_product_types" ("id")
        on delete restrict;
