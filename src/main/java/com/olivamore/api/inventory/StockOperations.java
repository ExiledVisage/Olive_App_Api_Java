package com.olivamore.api.inventory;

import com.olivamore.api.inventory.storagemovement.StorageMovementService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Stok hareketlerinin diger modullere acik kismi: uretim asamasinin (depo hareketi uzantisi) yaptigi tank
 * girisi, cikisi ve transferi; dolum kosusunun tanktan cekisi. Kurallar ve bakiye
 * {@code StorageMovementService}'te, tank sayfasindakiyle ayni. Proses hareketleri {@code BATCH_PROCESS}
 * kaynakli yazilir, bolunen materyal prosese baglanir; dolum kosusunun cikisi {@code MANUAL} kaynakli ve
 * kosuya bagli.
 */
@Component
public class StockOperations {

	private final StorageMovementService movements;

	StockOperations(StorageMovementService movements) {
		this.movements = movements;
	}

	/** Partideki materyalin (ya da bir kisminin) tanka alinmasi. */
	public void inbound(long unitId, long materialId, BigDecimal quantity, long batchProcessId, String note) {
		movements.inbound(unitId, materialId, quantity, batchProcessId, note);
	}

	/** Tanktan cikan miktar tuketilir. */
	public void outbound(long unitId, long materialId, BigDecimal quantity, long batchProcessId, String note) {
		movements.outbound(unitId, materialId, quantity, batchProcessId, note, null);
	}

	/** Dolum kosusunun tanktan cektigi yag (MANUAL kaynakli). */
	public void outboundForRun(long unitId, long materialId, BigDecimal quantity, String note, long runId) {
		movements.outbound(unitId, materialId, quantity, null, note, runId);
	}

	/** Tanklar arasi transfer. */
	public void transfer(long fromUnitId, long toUnitId, long materialId, BigDecimal quantity, long batchProcessId,
		String note) {
		movements.transfer(fromUnitId, toUnitId, materialId, quantity, batchProcessId, note);
	}

}
