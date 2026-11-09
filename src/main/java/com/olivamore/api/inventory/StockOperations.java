package com.olivamore.api.inventory;

import com.olivamore.api.inventory.storagemovement.StorageMovementService;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.Db;
import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Stok hareketlerinin diger modullere acik kismi: uretim asamasinin (depo hareketi uzantisi) yaptigi tank
 * girisi, cikisi ve transferi. Kurallar ve bakiye {@code StorageMovementService}'te, tank sayfasindakiyle ayni.
 * Proses hareketleri {@code BATCH_PROCESS} kaynakli yazilir, bolunen materyal prosese baglanir; dolum kosusunun
 * cikisi {@code MANUAL} kaynakli ve kosuya bagli.
 */
@Component
public class StockOperations {

	private final StorageMovementService movements;
	private final Db db;

	StockOperations(StorageMovementService movements, Db db) {
		this.movements = movements;
		this.db = db;
	}

	/** {@code createInboundMovement}: partideki materyalin (ya da bir kisminin) tanka alinmasi. */
	public void inbound(Row unit, Row material, double quantity, Row batchProcess, String note) {
		movements.inbound(unit, material, quantity, batchProcess, note);
	}

	/** {@code createOutboundMovement}: tanktan cikan miktar tuketilir. */
	public void outbound(Row unit, Row material, double quantity, Row batchProcess, String note) {
		movements.outbound(unit, material, quantity, batchProcess, note);
	}

	/** {@code createOutboundMovement(..., $finishedProductRunId)}: dolum kosusunun tanktan cektigi yag (MANUAL kaynakli). */
	public void outboundForRun(long unitId, long materialId, BigDecimal quantity, String note, long runId) {
		Row unit = db.find("storage_units", unitId).orElseThrow(() -> new ModelNotFoundException("StorageUnit", unitId));
		Row material = db.find("materials", materialId).orElseThrow(() -> new ModelNotFoundException("Material", materialId));
		movements.outbound(unit, material, quantity.doubleValue(), null, note, runId);
	}

	/** {@code transferBetweenUnits} */
	public void transfer(Row from, Row to, Row material, double quantity, Row batchProcess, String note) {
		movements.transfer(from, to, material, quantity, batchProcess, note);
	}

}
