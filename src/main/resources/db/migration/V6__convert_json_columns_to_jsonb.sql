-- JSON tutan kolonlar jsonb olur. Veri korunur; nesne anahtarlarinin sirasi ve tekrarli anahtarlar jsonb kuralina
-- gore normalize edilir. Tanimlar ve tipli deger kolonlari iliskisel kalir.

alter table batch_processes alter column operation_data type jsonb using operation_data::jsonb;
alter table process_type_fields alter column options type jsonb using options::jsonb;
alter table batch_process_field_values alter column value_json type jsonb using value_json::jsonb;
alter table stage_extension_type_fields alter column default_value_json type jsonb using default_value_json::jsonb;
alter table stage_extension_type_fields alter column options_json type jsonb using options_json::jsonb;
alter table production_stage_extension_values alter column value_json type jsonb using value_json::jsonb;
alter table material_attribute_values alter column value_json type jsonb using value_json::jsonb;
alter table batch_process_reviews alter column operation_data type jsonb using operation_data::jsonb;
alter table analytics_reports alter column config type jsonb using config::jsonb;
