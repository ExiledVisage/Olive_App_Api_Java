package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Casts;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Relations;
import com.olivamore.api.shared.persistence.Row;
import com.olivamore.api.shared.php.Php;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.olivamore.api.production.batchprocess.stage.StageExtensions.Extension;
import org.springframework.stereotype.Service;

/** Laravel {@code StorageMovementBatchProcessService}: tanka giris, cikis ya da transfer (asamada sabitlenmis tur). */
@Service
class StorageMovementBatchProcessService extends StageBatchProcessService {

	StorageMovementBatchProcessService(Db db, StockOperations stock) {
		super(db, stock);
	}

	void storageMovement(Row process, List<Extension> extensions, Map<String, Object> data) {
		String pinned = StageExtensions.pinnedAction(extensions);
		Object sent = data.get("action");
		String action;
		if (pinned == null) {
			action = sent == null ? "inbound" : Php.strval(sent);
		}
		else {
			if (sent != null && !pinned.equals(sent)) {
				throw new BusinessRuleException("Bu aşamada hareket türü '" + pinned + "' olarak sabitlenmiş; '" + Php.strval(sent)
					+ "' gönderilemez.");
			}
			action = pinned;
		}
		Object note = data.get("note") != null ? data.get("note") : process.get("note");
		String noteText = note == null ? null : Php.strval(note);
		double quantity = data.get("quantity") != null ? Php.floatval(data.get("quantity")) : 0;
		switch (action) {
			case "inbound", "outbound" -> {
				Row material = storageMaterial(process, extensions, data);
				Object unitId = data.get("storage_unit_id");
				if (!truthy(unitId) || quantity <= 0) {
					throw new BusinessRuleException(("inbound".equals(action) ? "Inbound" : "Outbound")
						+ " için storage_unit_id ve quantity zorunludur.");
				}
				Row unit = findOrFail("storage_units", "StorageUnit", unitId);
				if ("inbound".equals(action)) {
					stock.inbound(unit, material, quantity, process, noteText);
				}
				else {
					stock.outbound(unit, material, quantity, process, noteText);
				}
			}
			case "transfer" -> {
				Row material = storageMaterial(process, extensions, data);
				Object fromId = data.get("from_storage_unit_id");
				Object toId = data.get("to_storage_unit_id");
				if (!truthy(fromId) || !truthy(toId) || quantity <= 0) {
					throw new BusinessRuleException("Transfer için from_storage_unit_id, to_storage_unit_id ve quantity zorunludur.");
				}
				Row from = findOrFail("storage_units", "StorageUnit", fromId);
				Row to = findOrFail("storage_units", "StorageUnit", toId);
				stock.transfer(from, to, material, quantity, process, noteText);
			}
			default -> throw new BusinessRuleException("Geçersiz storage movement action gönderildi.");
		}
	}

	/** Uzantinin girdi kategorilerindeki (tanimliysa) aktif parti materyali; secilen ya da tek. */
	private Row storageMaterial(Row process, List<Extension> extensions, Map<String, Object> data) {
		Extension extension = first(extensions, "storage_movement");
		List<Long> categories = extension == null ? List.of() : extension.inputProductIds();
		if (truthy(data.get("material_id"))) {
			Row material = findOrFail("materials", "Material", data.get("material_id"));
			if (Php.intval(material.get("production_batch_id")) != Php.intval(process.get("production_batch_id"))) {
				throw new BusinessRuleException("Seçilen material bu batch’e ait değil.");
			}
			Row type = db.find("production_product_types", Php.intval(material.get("production_product_type_id"))).orElseThrow();
			if (!categories.isEmpty() && !categories.contains(Php.intval(type.get("production_product_id")))) {
				throw new BusinessRuleException("Seçilen material (" + Php.strval(type.get("name")) + ") bu aşamanın girdi ürünlerinden değil.");
			}
			return material;
		}
		List<Row> materials = categories.isEmpty()
			? db.select("select * from materials where production_batch_id = ? and status = ?", process.get("production_batch_id"), "active")
			: candidateMaterials(process, categories);
		if (materials.isEmpty()) {
			throw new BusinessRuleException("Storage movement için aktif material bulunamadı.");
		}
		if (materials.size() > 1) {
			throw new BusinessRuleException("Birden fazla aktif material bulundu. material_id gönderilmelidir.");
		}
		return materials.getFirst();
	}

}
