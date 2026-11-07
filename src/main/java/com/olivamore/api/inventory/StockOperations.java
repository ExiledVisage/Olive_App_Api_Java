package com.olivamore.api.inventory;

import com.olivamore.api.shared.persistence.Row;
import org.springframework.stereotype.Component;

/**
 * Stok hareketlerinin diger modullere acik kismi: uretim asamasinin (depo hareketi uzantisi) yaptigi tank
 * girisi, cikisi ve transferi. Kurallar ve bakiye {@code StorageMovements}'ta, tank sayfasindakiyle ayni.
 * Hareketler {@code BATCH_PROCESS} kaynakli yazilir; bolunen materyal prosese baglanir.
 */
@Component
public class StockOperations {

	private final StorageMovements movements;

	StockOperations(StorageMovements movements) {
		this.movements = movements;
	}

	/** {@code createInboundMovement}: partideki materyalin (ya da bir kisminin) tanka alinmasi. */
	public void inbound(Row unit, Row material, double quantity, Row batchProcess, String note) {
		movements.inbound(unit, material, quantity, batchProcess, note);
	}

	/** {@code createOutboundMovement}: tanktan cikan miktar tuketilir. */
	public void outbound(Row unit, Row material, double quantity, Row batchProcess, String note) {
		movements.outbound(unit, material, quantity, batchProcess, note);
	}

	/** {@code transferBetweenUnits} */
	public void transfer(Row from, Row to, Row material, double quantity, Row batchProcess, String note) {
		movements.transfer(from, to, material, quantity, batchProcess, note);
	}

}
