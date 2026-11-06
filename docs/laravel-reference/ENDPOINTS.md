# Laravel API Envanteri

`php artisan route:list --json` çıktısından ve controller kaynak kodundan otomatik üretildi.
Kaynak: `Olive_Production_Tracking_App_Back` `dev` branch'i, `ba61990`. Ham veri: [routes.json](routes.json)

- **Erişim:** Spatie izni ya da rolü. "giriş yeterli" = sadece Sanctum token'ı gerekir.
- **Request:** validation'ı yapan FormRequest sınıfı (`app/Http/Requests`).
- **Cevap:** kullanılan Resource sınıfı (`[]` = koleksiyon, sayfalıysa `data/links/meta`). `json` = elle yazılmış `response()->json`, Java'da birebir kopyalanmalı.

## Özet

| Faz | Modül | Endpoint |
|---|---|---|
| 2 | identity | 17 |
| 3 | app-release | 2 |
| 4 | catalog | 58 |
| 5 | inventory | 20 |
| 6 | production | 11 |
| 7 | packaging | 13 |
| 8 | finished-product | 33 |
| 9 | sales | 20 |
| 10 | notification | 6 |
| 11 | analytics | 11 |
| | **Toplam** | **191** |

## Faz 2: identity

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| POST | `/api/auth/login` | AuthController@login | herkese açık (throttle: login) | LoginRequest | UserResource |
| POST | `/api/auth/logout` | AuthController@logout | giriş yeterli | - | json |
| GET | `/api/auth/me` | AuthController@me | giriş yeterli | - | UserResource |
| POST | `/api/auth/register` | AuthController@register | herkese açık (throttle: register) | RegisterRequest | UserResource |
| GET | `/api/permissions` | PermissionController@index | role:admin | - | PermissionResource[] |
| GET | `/api/roles` | RoleController@index | role:admin | - | RoleResource[] |
| POST | `/api/roles` | RoleController@store | role:admin | StoreRoleRequest | RoleResource |
| DELETE | `/api/roles/{role}` | RoleController@destroy | role:admin | - | json |
| GET | `/api/roles/{role}` | RoleController@show | role:admin | - | RoleResource |
| PUT|PATCH | `/api/roles/{role}` | RoleController@update | role:admin | UpdateRoleRequest | RoleResource |
| GET | `/api/user` | Closure | giriş yeterli | - | User modeli (ham, $hidden hariç) |
| GET | `/api/users` | UserController@index | role:admin | - | UserResource[] |
| POST | `/api/users` | UserController@store | role:admin | StoreUserRequest | UserResource |
| DELETE | `/api/users/{user}` | UserController@destroy | role:admin | - | json |
| GET | `/api/users/{user}` | UserController@show | role:admin | - | UserResource |
| PUT|PATCH | `/api/users/{user}` | UserController@update | role:admin | UpdateUserRequest | UserResource |
| PATCH | `/api/users/{user}/roles` | UserRoleController@update | role:admin | UpdateUserRolesRequest | UserResource |

## Faz 3: app-release

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| GET | `/api/app/download` | AppReleaseController@download | herkese açık (throttle: 30,1) | - | dosya |
| GET | `/api/app/version` | AppReleaseController@version | herkese açık (throttle: 30,1) | - | json |

## Faz 4: catalog

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| GET | `/api/attribute-definitions` | AttributeDefinitionController@index | attribute_definition.view | - | AttributeDefinitionResource[] |
| GET | `/api/process-types` | ProcessTypeController@index | process_type.manage | - | ProcessTypeResource[] |
| POST | `/api/process-types` | ProcessTypeController@store | process_type.manage | StoreProcessTypeRequest | ProcessTypeResource |
| DELETE | `/api/process-types/{process_type}` | ProcessTypeController@destroy | process_type.manage | - | json |
| GET | `/api/process-types/{process_type}` | ProcessTypeController@show | process_type.manage | - | ProcessTypeResource |
| PUT|PATCH | `/api/process-types/{process_type}` | ProcessTypeController@update | process_type.manage | UpdateProcessTypeRequest | ProcessTypeResource |
| GET | `/api/process-types/{process_type}/fields` | ProcessTypeFieldController@index | process_type_field.manage | - | ProcessTypeFieldResource[] |
| POST | `/api/process-types/{process_type}/fields` | ProcessTypeFieldController@store | process_type_field.manage | StoreProcessTypeFieldRequest | ProcessTypeFieldResource |
| DELETE | `/api/process-types/{process_type}/fields/{field}` | ProcessTypeFieldController@destroy | process_type_field.manage | - | json |
| GET | `/api/process-types/{process_type}/fields/{field}` | ProcessTypeFieldController@show | process_type_field.manage | - | ProcessTypeFieldResource |
| PUT|PATCH | `/api/process-types/{process_type}/fields/{field}` | ProcessTypeFieldController@update | process_type_field.manage | UpdateProcessTypeFieldRequest | ProcessTypeFieldResource |
| GET | `/api/product-suppliers` | ProductSupplierController@index | product_supplier.view | - | ProductSupplierResource[] |
| POST | `/api/product-suppliers` | ProductSupplierController@store | product_supplier.manage | StoreProductSupplierRequest | ProductSupplierResource |
| DELETE | `/api/product-suppliers/{product_supplier}` | ProductSupplierController@destroy | product_supplier.manage | - | json |
| GET | `/api/product-suppliers/{product_supplier}` | ProductSupplierController@show | product_supplier.view | - | ProductSupplierResource |
| PATCH | `/api/product-suppliers/{product_supplier}` | ProductSupplierController@update | product_supplier.manage | UpdateProductSupplierRequest | ProductSupplierResource |
| GET | `/api/production-lines` | ProductionLineController@index | production_line.view | - | ProductionLineResource[] |
| POST | `/api/production-lines` | ProductionLineController@store | production_line.manage | StoreProductionLineRequest | ProductionLineResource |
| PUT | `/api/production-lines/{productionLine}/stages/{stage}/extensions` | ProductionStageExtensionController@sync | production_stage.manage | SyncProductionStageExtensionsRequest | ProductionStageResource |
| DELETE | `/api/production-lines/{production_line}` | ProductionLineController@destroy | production_line.manage | - | json |
| GET | `/api/production-lines/{production_line}` | ProductionLineController@show | production_line.view | - | ProductionLineResource |
| PATCH | `/api/production-lines/{production_line}` | ProductionLineController@update | production_line.manage | UpdateProductionLineRequest | ProductionLineResource |
| PUT | `/api/production-lines/{production_line}` | ProductionLineController@update | production_line.manage | UpdateProductionLineRequest | ProductionLineResource |
| GET | `/api/production-lines/{production_line}/stages` | ProductionStageController@index | production_stage.manage | - | ProductionStageResource[] |
| POST | `/api/production-lines/{production_line}/stages` | ProductionStageController@store | production_stage.manage | StoreProductionStageRequest | ProductionStageResource |
| DELETE | `/api/production-lines/{production_line}/stages/{stage}` | ProductionStageController@destroy | production_stage.manage | - | json |
| GET | `/api/production-lines/{production_line}/stages/{stage}` | ProductionStageController@show | production_stage.manage | - | ProductionStageResource |
| PUT|PATCH | `/api/production-lines/{production_line}/stages/{stage}` | ProductionStageController@update | production_stage.manage | UpdateProductionStageRequest | ProductionStageResource |
| GET | `/api/production-product-types` | ProductionProductTypeController@index | production_product_type.view | - | ProductionProductTypeResource[] |
| POST | `/api/production-product-types` | ProductionProductTypeController@store | production_product_type.manage | StoreProductionProductTypeRequest | ProductionProductTypeResource |
| DELETE | `/api/production-product-types/{productionProductType}` | ProductionProductTypeController@destroy | production_product_type.manage | - | json |
| GET | `/api/production-product-types/{productionProductType}` | ProductionProductTypeController@show | production_product_type.view | - | ProductionProductTypeResource |
| PATCH | `/api/production-product-types/{productionProductType}` | ProductionProductTypeController@update | production_product_type.manage | UpdateProductionProductTypeRequest | ProductionProductTypeResource |
| PUT | `/api/production-product-types/{productionProductType}` | ProductionProductTypeController@update | production_product_type.manage | UpdateProductionProductTypeRequest | ProductionProductTypeResource |
| GET | `/api/production-product-types/{productionProductType}/flow` | ProductionProductTypeController@flow | production_product_type.view | - | ProductionProductTypeResource, ProductionLineResource[] |
| GET | `/api/production-product-types/{productionProductType}/production-lines` | ProductionProductTypeController@productionLines | production_product_type.view | - | ProductionLineResource[] |
| GET | `/api/production-products` | ProductionProductController@index | production_product_type.view | - | ProductionProductResource[] |
| POST | `/api/production-products` | ProductionProductController@store | production_product_type.manage | StoreProductionProductRequest | ProductionProductResource |
| DELETE | `/api/production-products/{productionProduct}` | ProductionProductController@destroy | production_product_type.manage | - | json |
| GET | `/api/production-products/{productionProduct}` | ProductionProductController@show | production_product_type.view | - | ProductionProductResource |
| PATCH | `/api/production-products/{productionProduct}` | ProductionProductController@update | production_product_type.manage | UpdateProductionProductRequest | ProductionProductResource |
| PUT | `/api/production-products/{productionProduct}` | ProductionProductController@update | production_product_type.manage | UpdateProductionProductRequest | ProductionProductResource |
| GET | `/api/production-products/{productionProduct}/attribute-definitions` | ProductionProductAttributeDefinitionController@index | giriş yeterli | - | ProductionProductAttributeDefinitionResource[] |
| POST | `/api/production-products/{productionProduct}/attribute-definitions` | ProductionProductAttributeDefinitionController@store | giriş yeterli | StoreProductionProductAttributeDefinitionRequest | ProductionProductAttributeDefinitionResource |
| DELETE | `/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}` | ProductionProductAttributeDefinitionController@destroy | giriş yeterli | - | json |
| GET | `/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}` | ProductionProductAttributeDefinitionController@show | giriş yeterli | - | ProductionProductAttributeDefinitionResource |
| PATCH | `/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}` | ProductionProductAttributeDefinitionController@update | giriş yeterli | UpdateProductionProductAttributeDefinitionRequest | ProductionProductAttributeDefinitionResource |
| PUT | `/api/production-products/{productionProduct}/attribute-definitions/{attributeDefinition}` | ProductionProductAttributeDefinitionController@update | giriş yeterli | UpdateProductionProductAttributeDefinitionRequest | ProductionProductAttributeDefinitionResource |
| GET | `/api/stage-extension-types` | StageExtensionTypeController@index | production_stage.manage | - | StageExtensionTypeResource[] |
| GET | `/api/suppliers` | SupplierController@index | supplier.view | - | SupplierResource[] |
| POST | `/api/suppliers` | SupplierController@store | supplier.manage | StoreSupplierRequest | SupplierResource |
| DELETE | `/api/suppliers/{supplier}` | SupplierController@destroy | supplier.manage | - | json |
| GET | `/api/suppliers/{supplier}` | SupplierController@show | supplier.view | - | SupplierResource |
| PATCH | `/api/suppliers/{supplier}` | SupplierController@update | supplier.manage | UpdateSupplierRequest | SupplierResource |
| GET | `/api/suppliers/{supplier}/deliveries` | SupplierController@deliveries | supplier.view | - | json |
| POST | `/api/suppliers/{supplier}/fields` | SupplierController@storeField | supplier.manage | StoreSupplierFieldRequest | SupplierFieldResource |
| DELETE | `/api/suppliers/{supplier}/fields/{field}` | SupplierController@destroyField | supplier.manage | - | json |
| PATCH | `/api/suppliers/{supplier}/fields/{field}` | SupplierController@updateField | supplier.manage | UpdateSupplierFieldRequest | SupplierFieldResource |

## Faz 5: inventory

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| GET | `/api/materials` | MaterialController@index | storage_unit.view | - | json |
| GET | `/api/materials/{material}` | MaterialController@show | storage_unit.view | - | MaterialDetailResource |
| GET | `/api/materials/{material}/lineage` | MaterialController@lineage | storage_unit.view | - | json |
| POST | `/api/storage-movements/adjustment` | StorageMovementController@storeAdjustment | storage_movement.adjustment | StoreAdjustmentStorageMovementRequest | StorageMovementResource |
| POST | `/api/storage-movements/inbound` | StorageMovementController@storeInbound | storage_movement.create | StoreInboundStorageMovementRequest | StorageMovementResource |
| POST | `/api/storage-movements/opening` | StorageMovementController@storeOpening | storage_movement.create | StoreOpeningStorageMovementRequest | StorageMovementResource |
| POST | `/api/storage-movements/outbound` | StorageMovementController@storeOutbound | storage_movement.create | StoreOutboundStorageMovementRequest | StorageMovementResource |
| POST | `/api/storage-movements/transfer` | StorageMovementController@storeTransfer | storage_movement.transfer | StoreTransferStorageMovementRequest | StorageTransferResource |
| GET | `/api/storage-units` | StorageUnitController@index | storage_unit.view | - | StorageUnitResource[] |
| POST | `/api/storage-units` | StorageUnitController@store | storage_unit.manage | StoreStorageUnitRequest | StorageUnitResource |
| PUT | `/api/storage-units/{storageUnit}/allowed-production-product-types` | StorageUnitController@syncAllowedProductionProductTypes | storage_unit.manage | SyncStorageUnitAllowedProductionProductTypesRequest | StorageUnitResource |
| PUT | `/api/storage-units/{storageUnit}/allowed-production-products` | StorageUnitController@syncAllowedProductionProducts | storage_unit.manage | SyncStorageUnitAllowedProductionProductsRequest | StorageUnitResource |
| PUT | `/api/storage-units/{storageUnit}/attribute-rules` | StorageUnitController@syncAttributeRules | storage_unit.manage | SyncStorageUnitAttributeRulesRequest | StorageUnitResource |
| GET | `/api/storage-units/{storageUnit}/balances` | StorageUnitController@balances | storage_unit.view | - | StorageUnitBalanceResource[] |
| GET | `/api/storage-units/{storageUnit}/movement-history` | StorageMovementController@history | storage_movement.view | - | StorageMovementResource[] |
| GET | `/api/storage-units/{storageUnit}/movements` | StorageUnitController@movements | storage_unit.view | - | StorageMovementResource[] |
| DELETE | `/api/storage-units/{storage_unit}` | StorageUnitController@destroy | storage_unit.manage | - | json |
| GET | `/api/storage-units/{storage_unit}` | StorageUnitController@show | storage_unit.view | - | StorageUnitResource |
| PATCH | `/api/storage-units/{storage_unit}` | StorageUnitController@update | storage_unit.manage | UpdateStorageUnitRequest | StorageUnitResource |
| PUT | `/api/storage-units/{storage_unit}` | StorageUnitController@update | storage_unit.manage | UpdateStorageUnitRequest | StorageUnitResource |

## Faz 6: production

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| GET | `/api/batch-processes/{batchProcess}` | BatchProcessController@show | batch_process.view | - | BatchProcessResource |
| PATCH | `/api/batch-processes/{batchProcess}` | BatchProcessController@update | batch_process.view | UpdateBatchProcessRequest | BatchProcessResource |
| POST | `/api/batch-processes/{batchProcess}/approve` | BatchProcessController@approve | batch_process.approve | ReviewBatchProcessRequest | BatchProcessResource |
| POST | `/api/batch-processes/{batchProcess}/reject` | BatchProcessController@reject | batch_process.approve | ReviewBatchProcessRequest | BatchProcessResource |
| POST | `/api/batch-processes/{batchProcess}/sync-field-values` | BatchProcessController@syncFieldValues | batch_process.fill | SyncBatchProcessFieldValuesRequest | BatchProcessResource |
| GET | `/api/production-batches` | ProductionBatchController@index | production_batch.view | - | ProductionBatchResource[] |
| POST | `/api/production-batches` | ProductionBatchController@store | production_batch.create | StoreProductionBatchRequest | ProductionBatchResource |
| GET | `/api/production-batches/calendar` | ProductionBatchController@calendar | production_batch.view | - | json |
| DELETE | `/api/production-batches/{production_batch}` | ProductionBatchController@destroy | production_batch.delete | - | json |
| GET | `/api/production-batches/{production_batch}` | ProductionBatchController@show | production_batch.view | - | ProductionBatchResource |
| PATCH | `/api/production-batches/{production_batch}` | ProductionBatchController@update | production_batch.update | UpdateProductionBatchRequest | ProductionBatchResource |

## Faz 7: packaging

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| GET | `/api/packaging-items` | PackagingItemController@index | packaging_item.view | - | PackagingItemResource[] |
| POST | `/api/packaging-items` | PackagingItemController@store | packaging_item.manage | StorePackagingItemRequest | PackagingItemResource |
| DELETE | `/api/packaging-items/{packaging_item}` | PackagingItemController@destroy | packaging_item.manage | - | json |
| GET | `/api/packaging-items/{packaging_item}` | PackagingItemController@show | packaging_item.view | - | PackagingItemResource |
| PATCH | `/api/packaging-items/{packaging_item}` | PackagingItemController@update | packaging_item.manage | UpdatePackagingItemRequest | PackagingItemResource |
| PUT | `/api/packaging-items/{packaging_item}` | PackagingItemController@update | packaging_item.manage | UpdatePackagingItemRequest | PackagingItemResource |
| GET | `/api/packaging-items/{packaging_item}/lots` | PackagingLotController@indexForItem | packaging_lot.view | - | PackagingLotResource[] |
| POST | `/api/packaging-items/{packaging_item}/lots` | PackagingLotController@store | packaging_lot.receive | StorePackagingLotRequest | PackagingLotResource |
| GET | `/api/packaging-lots` | PackagingLotController@index | packaging_lot.view | - | PackagingLotResource[] |
| DELETE | `/api/packaging-lots/{packaging_lot}` | PackagingLotController@destroy | packaging_lot.receive | - | json |
| GET | `/api/packaging-lots/{packaging_lot}` | PackagingLotController@show | packaging_lot.view | - | PackagingLotResource |
| PATCH | `/api/packaging-lots/{packaging_lot}` | PackagingLotController@update | packaging_lot.receive | UpdatePackagingLotRequest | PackagingLotResource |
| POST | `/api/packaging-lots/{packaging_lot}/adjustment` | PackagingLotController@adjust | packaging_lot.adjustment | AdjustPackagingLotRequest | PackagingLotResource |

## Faz 8: finished-product

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| GET | `/api/finished-product-recipes` | FinishedProductRecipeController@index | finished_product_recipe.view | - | FinishedProductRecipeResource[] |
| POST | `/api/finished-product-recipes` | FinishedProductRecipeController@store | finished_product_recipe.manage | StoreFinishedProductRecipeRequest | FinishedProductRecipeResource |
| GET | `/api/finished-product-recipes/{finishedProductRecipe}/versions` | FinishedProductRecipeController@versions | giriş yeterli | - | FinishedProductRecipeResource[] |
| DELETE | `/api/finished-product-recipes/{finished_product_recipe}` | FinishedProductRecipeController@destroy | finished_product_recipe.manage | - | json |
| GET | `/api/finished-product-recipes/{finished_product_recipe}` | FinishedProductRecipeController@show | finished_product_recipe.view | - | FinishedProductRecipeResource |
| PATCH | `/api/finished-product-recipes/{finished_product_recipe}` | FinishedProductRecipeController@update | finished_product_recipe.manage | UpdateFinishedProductRecipeRequest | FinishedProductRecipeResource |
| PUT | `/api/finished-product-recipes/{finished_product_recipe}` | FinishedProductRecipeController@update | finished_product_recipe.manage | UpdateFinishedProductRecipeRequest | FinishedProductRecipeResource |
| GET | `/api/finished-product-runs` | FinishedProductRunController@index | finished_product_run.view | - | FinishedProductRunResource[] |
| POST | `/api/finished-product-runs` | FinishedProductRunController@store | finished_product_run.create | StoreFinishedProductRunRequest | FinishedProductRunResource |
| DELETE | `/api/finished-product-runs/{finishedProductRun}` | FinishedProductRunController@destroy | finished_product_run.create | - | json |
| GET | `/api/finished-product-runs/{finishedProductRun}` | FinishedProductRunController@show | finished_product_run.view | - | FinishedProductRunResource |
| PATCH | `/api/finished-product-runs/{finishedProductRun}/attribute-values/{attributeValue}` | FinishedProductRunController@updateAttributeValue | finished_product_run.complete | UpdateAttributeValueRequest | json |
| PUT | `/api/finished-product-runs/{finishedProductRun}/packagings` | FinishedProductRunController@syncPackagings | finished_product_run.create | SyncRunPackagingsRequest | FinishedProductRunResource |
| PATCH | `/api/finished-product-runs/{finishedProductRun}/status` | FinishedProductRunController@updateStatus | finished_product_run.complete | UpdateFinishedProductRunStatusRequest | FinishedProductRunResource |
| GET | `/api/finished-product-storages` | FinishedProductStorageController@index | finished_product_storage.view | - | FinishedProductStorageResource[] |
| POST | `/api/finished-product-storages` | FinishedProductStorageController@store | finished_product_storage.manage | StoreFinishedProductStorageRequest | FinishedProductStorageResource |
| GET | `/api/finished-product-storages/{finishedProductStorage}/movements` | FinishedProductStorageController@movements | finished_product_storage.view | - | FinishedProductStockMovementResource[] |
| POST | `/api/finished-product-storages/{finishedProductStorage}/slots` | FinishedProductStorageController@storeSlot | finished_product_storage.manage | StoreFinishedProductStorageSlotRequest | FinishedProductStorageSlotResource |
| DELETE | `/api/finished-product-storages/{finishedProductStorage}/slots/{slot}` | FinishedProductStorageController@destroySlot | finished_product_storage.manage | - | json |
| PATCH | `/api/finished-product-storages/{finishedProductStorage}/slots/{slot}` | FinishedProductStorageController@updateSlot | finished_product_storage.manage | UpdateFinishedProductStorageSlotRequest | FinishedProductStorageSlotResource |
| POST | `/api/finished-product-storages/{finishedProductStorage}/stock-in` | FinishedProductStorageController@stockIn | finished_product_storage.stock_in | StockInRequest | FinishedProductStorageResource |
| POST | `/api/finished-product-storages/{finishedProductStorage}/stock-out` | FinishedProductStorageController@stockOut | finished_product_storage.stock_out | StockOutRequest | FinishedProductStorageResource |
| POST | `/api/finished-product-storages/{finishedProductStorage}/transfer` | FinishedProductStorageController@transfer | finished_product_storage.stock_in | TransferStockRequest | FinishedProductStorageResource |
| DELETE | `/api/finished-product-storages/{finished_product_storage}` | FinishedProductStorageController@destroy | finished_product_storage.manage | - | json |
| GET | `/api/finished-product-storages/{finished_product_storage}` | FinishedProductStorageController@show | finished_product_storage.view | - | FinishedProductStorageResource |
| PATCH | `/api/finished-product-storages/{finished_product_storage}` | FinishedProductStorageController@update | finished_product_storage.manage | UpdateFinishedProductStorageRequest | FinishedProductStorageResource |
| PUT | `/api/finished-product-storages/{finished_product_storage}` | FinishedProductStorageController@update | finished_product_storage.manage | UpdateFinishedProductStorageRequest | FinishedProductStorageResource |
| GET | `/api/finished-products` | FinishedProductController@index | finished_product.view | - | FinishedProductResource[] |
| POST | `/api/finished-products` | FinishedProductController@store | finished_product.manage | StoreFinishedProductRequest | FinishedProductResource |
| DELETE | `/api/finished-products/{finished_product}` | FinishedProductController@destroy | finished_product.manage | - | json |
| GET | `/api/finished-products/{finished_product}` | FinishedProductController@show | finished_product.view | - | FinishedProductResource |
| PATCH | `/api/finished-products/{finished_product}` | FinishedProductController@update | finished_product.manage | UpdateFinishedProductRequest | FinishedProductResource |
| PUT | `/api/finished-products/{finished_product}` | FinishedProductController@update | finished_product.manage | UpdateFinishedProductRequest | FinishedProductResource |

## Faz 9: sales

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| GET | `/api/customers` | CustomerController@index | customer.view | - | CustomerResource[] |
| POST | `/api/customers` | CustomerController@store | customer.manage | StoreCustomerRequest | CustomerResource |
| DELETE | `/api/customers/{customer}` | CustomerController@destroy | customer.manage | - | json |
| GET | `/api/customers/{customer}` | CustomerController@show | customer.view | - | CustomerResource |
| PATCH | `/api/customers/{customer}` | CustomerController@update | customer.manage | UpdateCustomerRequest | CustomerResource |
| PUT | `/api/customers/{customer}` | CustomerController@update | customer.manage | UpdateCustomerRequest | CustomerResource |
| GET | `/api/customers/{customer}/addresses` | CustomerController@addresses | customer.view | - | CustomerAddressResource[] |
| POST | `/api/customers/{customer}/addresses` | CustomerController@storeAddress | customer.manage | StoreCustomerAddressRequest | CustomerAddressResource |
| DELETE | `/api/customers/{customer}/addresses/{address}` | CustomerController@destroyAddress | customer.manage | - | json |
| PATCH | `/api/customers/{customer}/addresses/{address}` | CustomerController@updateAddress | customer.manage | UpdateCustomerAddressRequest | CustomerAddressResource |
| POST | `/api/customers/{customer}/preferred-recipes` | CustomerController@storePreferredRecipe | customer.manage | StoreCustomerPreferredRecipeRequest | json |
| DELETE | `/api/customers/{customer}/preferred-recipes/{recipe}` | CustomerController@destroyPreferredRecipe | customer.manage | - | json |
| GET | `/api/customers/{customer}/recipes` | CustomerController@recipes | customer.view | - | FinishedProductRecipeResource |
| GET | `/api/orders` | OrderController@index | order.view | - | OrderResource[] |
| POST | `/api/orders` | OrderController@store | order.create | StoreOrderRequest | OrderResource |
| DELETE | `/api/orders/{order}` | OrderController@destroy | order.update | - | json |
| GET | `/api/orders/{order}` | OrderController@show | order.view | - | OrderResource |
| PATCH | `/api/orders/{order}` | OrderController@update | order.update | UpdateOrderRequest | OrderResource |
| PATCH | `/api/orders/{order}/status` | OrderController@updateStatus | order.update_status | UpdateOrderStatusRequest | OrderResource |
| GET | `/api/orders/{order}/stock-check` | OrderController@stockCheck | order.view | - | json |

## Faz 10: notification

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| GET | `/api/notifications` | NotificationController@index | giriş yeterli | - | UserNotificationResource[] |
| POST | `/api/notifications/read-all` | NotificationController@markAllRead | giriş yeterli | - | json |
| GET | `/api/notifications/unread-count` | NotificationController@unreadCount | giriş yeterli | - | json |
| POST | `/api/notifications/{notification}/read` | NotificationController@markRead | giriş yeterli | - | UserNotificationResource |
| POST | `/api/nudges` | NotificationController@nudge | notification.send | SendNudgeRequest | UserNotificationResource[] |
| GET | `/api/nudges/targets` | NotificationController@nudgeTargets | notification.send | - | json |

## Faz 11: analytics

| Metot | URI | Handler | Erişim | Request | Cevap |
|---|---|---|---|---|---|
| GET | `/api/analytics/catalog` | AnalyticsController@catalog | analytics.view | - | json |
| GET | `/api/analytics/customers` | AnalyticsController@customers | analytics.view | - | json |
| GET | `/api/analytics/customers/{customer}` | AnalyticsController@customer | analytics.view | - | json |
| GET | `/api/analytics/query` | AnalyticsController@query | analytics.view | - | json |
| GET | `/api/analytics/reports` | AnalyticsController@reports | analytics.view | - | json |
| POST | `/api/analytics/reports` | AnalyticsController@storeReport | analytics.view | SaveAnalyticsReportRequest | json |
| DELETE | `/api/analytics/reports/{report}` | AnalyticsController@destroyReport | analytics.view | - | json |
| PATCH | `/api/analytics/reports/{report}` | AnalyticsController@updateReport | analytics.view | SaveAnalyticsReportRequest | json |
| GET | `/api/analytics/suppliers` | AnalyticsController@suppliers | analytics.view | - | json |
| GET | `/api/analytics/yield` | AnalyticsController@yield | analytics.view | - | json |
| GET | `/api/daily-summary` | DailySummaryController@show | giriş yeterli | - | json |
