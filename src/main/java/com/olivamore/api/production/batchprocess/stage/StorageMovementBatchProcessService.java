package com.olivamore.api.production.batchprocess.stage;

import com.olivamore.api.inventory.StockOperations;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Extension;
import com.olivamore.api.production.batchprocess.stage.StageQueries.Material;
import com.olivamore.api.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/** Tanka giris, cikis ya da transfer (asamada sabitlenmis tur). */
@Service
class StorageMovementBatchProcessService extends StageBatchProcessService {

	StorageMovementBatchProcessService(StageQueries queries, StockOperations stock) {
		super(queries, stock);
	}

	void storageMovement(StageProcess process, List<Extension> extensions, OperationData data) {
		String action = action(extensions, data);
		String note = data.text("note") != null ? data.text("note") : process.note();
		BigDecimal quantity = data.quantity("quantity");
		Extension extension = first(extensions, "storage_movement");
		switch (action) {
			case "inbound", "outbound" -> {
				Material material = batchMaterial(process, extension, data, "Storage movement");
				Long unitId = data.id("storage_unit_id");
				if (unitId == null || quantity.signum() <= 0) {
					throw new BusinessRuleException(("inbound".equals(action) ? "Inbound" : "Outbound")
						+ " için storage_unit_id ve quantity zorunludur.");
				}
				if ("inbound".equals(action)) {
					stock.inbound(unitId, material.id(), quantity, process.id(), note);
				}
				else {
					stock.outbound(unitId, material.id(), quantity, process.id(), note);
				}
			}
			case "transfer" -> {
				Material material = batchMaterial(process, extension, data, "Storage movement");
				Long fromId = data.id("from_storage_unit_id");
				Long toId = data.id("to_storage_unit_id");
				if (fromId == null || toId == null || quantity.signum() <= 0) {
					throw new BusinessRuleException("Transfer için from_storage_unit_id, to_storage_unit_id ve quantity zorunludur.");
				}
				stock.transfer(fromId, toId, material.id(), quantity, process.id(), note);
			}
			default -> throw new BusinessRuleException("Geçersiz storage movement action gönderildi.");
		}
	}

	/** Gonderilen hareket turu; sabitlenmisse farkli tur reddedilir. */
	private static String action(List<Extension> extensions, OperationData data) {
		String pinned = StageExtensions.pinnedAction(extensions);
		String sent = data.text("action");
		if (pinned == null) {
			return sent == null ? "inbound" : sent;
		}
		if (sent != null && !pinned.equals(sent)) {
			throw new BusinessRuleException("Bu aşamada hareket türü '" + pinned + "' olarak sabitlenmiş; '" + sent
				+ "' gönderilemez.");
		}
		return pinned;
	}

}
