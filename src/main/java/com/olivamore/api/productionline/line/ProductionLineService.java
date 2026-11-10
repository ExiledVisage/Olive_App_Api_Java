package com.olivamore.api.productionline.line;

import com.olivamore.api.productionline.ProductCatalog;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class ProductionLineService {

	private static final String INPUTS = "input_production_products";
	private static final String OUTPUTS = "output_production_products";

	private final ProductionLineRepository lines;
	private final LineInputProductRepository inputs;
	private final LineOutputProductRepository outputs;
	private final ProductCatalog catalog;

	ProductionLineService(ProductionLineRepository lines, LineInputProductRepository inputs,
		LineOutputProductRepository outputs, ProductCatalog catalog) {
		this.lines = lines;
		this.inputs = inputs;
		this.outputs = outputs;
		this.catalog = catalog;
	}

	@Transactional(readOnly = true)
	List<ProductionLine> all() {
		return lines.findAllByOrderByCreatedAtDesc();
	}

	@Transactional(readOnly = true)
	ProductionLine get(long id) {
		return find(id);
	}

	@Transactional
	ProductionLine create(CreateProductionLineRequest request) {
		assertProducts(request.inputProductionProducts(), request.outputProductionProducts());
		ProductionLine line = lines.save(new ProductionLine(request));
		replaceInputs(line.getId(), request.inputProductionProducts());
		replaceOutputs(line.getId(), request.outputProductionProducts());
		return line;
	}

	@Transactional
	ProductionLine update(long id, UpdateProductionLineRequest request) {
		ProductionLine line = find(id);
		assertProducts(request.inputProductionProducts(), request.outputProductionProducts());
		line.apply(request);
		if (request.inputProductionProducts() != null) {
			replaceInputs(id, request.inputProductionProducts());
		}
		if (request.outputProductionProducts() != null) {
			replaceOutputs(id, request.outputProductionProducts());
		}
		return line;
	}

	@Transactional
	void delete(long id) {
		ProductionLine line = find(id);
		DeletionGuard.of("'" + line.getName() + "' hattı")
			.usedBy("üretim partisi", lines.countBatches(id))
			.check();
		lines.delete(line);
	}

	private ProductionLine find(long id) {
		return lines.findById(id).orElseThrow(() -> new ModelNotFoundException("ProductionLine", id));
	}

	private void replaceInputs(long lineId, List<LineProductRequest> items) {
		inputs.deleteForLine(lineId);
		for (int i = 0; i < items.size(); i++) {
			inputs.save(new LineInputProduct(lineId, items.get(i), i + 1));
		}
	}

	private void replaceOutputs(long lineId, List<LineProductRequest> items) {
		outputs.deleteForLine(lineId);
		for (int i = 0; i < items.size(); i++) {
			outputs.save(new LineOutputProduct(lineId, items.get(i), i + 1));
		}
	}

	private void assertProducts(List<LineProductRequest> inputItems, List<LineProductRequest> outputItems) {
		ValidationErrors errors = new ValidationErrors();
		check(errors, INPUTS, inputItems);
		check(errors, OUTPUTS, outputItems);
		errors.throwIfAny();
	}

	private void check(ValidationErrors errors, String side, List<LineProductRequest> items) {
		if (items == null) {
			return;
		}
		List<Long> products = items.stream().map(LineProductRequest::productionProductId).toList();
		List<Long> types = items.stream().map(LineProductRequest::productionProductTypeId).toList();
		errors.exists(side, "production_product_id", products, catalog.products(products).keySet());
		errors.distinct(side, "production_product_id", products);
		errors.exists(side, "production_product_type_id", types, catalog.productTypes(types).keySet());
	}

}
