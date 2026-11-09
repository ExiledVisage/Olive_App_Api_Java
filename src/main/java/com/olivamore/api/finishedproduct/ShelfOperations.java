package com.olivamore.api.finishedproduct;

import com.olivamore.api.finishedproduct.storage.FinishedProductStorageService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bitmis urun raflarinin diger modullere acik kismi: siparis hazirlanirken raftan alma ve hazir siparis
 * iptalinde rafa geri koyma. Dort sayim depo ekranindakiyle ayni yoldan guncellenir.
 */
@Component
public class ShelfOperations {

	private final FinishedProductStorageService flow;

	ShelfOperations(FinishedProductStorageService flow) {
		this.flow = flow;
	}

	/** Raftan alinacak: raf, urun, adet. */
	public record Pick(long slotId, long productId, long quantity) {
	}

	public void pickForOrder(long orderId, List<Pick> picks, Long actorId) {
		flow.pickForOrder(orderId,
			picks.stream().map(p -> new FinishedProductStorageService.Pick(p.slotId(), p.productId(),
				p.quantity())).toList(),
			actorId);
	}

	public void returnOrderPicks(long orderId, Long actorId) {
		flow.returnOrderPicks(orderId, actorId);
	}

}
