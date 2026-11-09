package com.olivamore.api.product.productiontype;

import com.olivamore.api.productionline.ProductionLineResponse;

import java.util.List;

record ProductTypeFlowResponse(
	ProductionProductTypeResponse productionProductType,
	List<ProductionLineResponse> inputProductionLines,
	List<ProductionLineResponse> outputProductionLines,
	List<ProductionLineResponse> productionLines) {
}
