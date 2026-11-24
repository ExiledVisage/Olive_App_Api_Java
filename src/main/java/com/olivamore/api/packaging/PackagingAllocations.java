package com.olivamore.api.packaging;

import com.olivamore.api.packaging.lot.LotAllocationService;
import com.olivamore.api.packaging.lot.RunScrapService;
import org.springframework.stereotype.Component;

import java.util.List;

/** Modulun bitmis urun tarafina acik kapisi: dolum kosusu icin ambalaj lotu tahsisi ve tuketimi. */
@Component
public class PackagingAllocations {

	/** Kosunun ihtiyaci: kalem basina toplam adet. */
	public record Need(long itemId, long quantity) {
	}

	/** Elle secilen lot ve adet. */
	public record Selection(long lotId, long quantity) {
	}

	private final LotAllocationService allocations;
	private final RunScrapService scraps;

	PackagingAllocations(LotAllocationService allocations, RunScrapService scraps) {
		this.allocations = allocations;
		this.scraps = scraps;
	}

	/**
	 * Kosuda kirik / hatali ambalaj; {@code lotId} bossa kosunun tahsis ettigi lot. @param reason broken,
	 * defective ya da other @return fire hareketinin id'si
	 */
	public long scrapForRun(long runId, long itemId, Long lotId, int quantity, String reason, String note,
		long userId) {
		return scraps.scrap(runId, itemId, lotId, quantity, reason, note, userId);
	}

	public void undoScrap(long runId, long movementId) {
		scraps.undo(runId, movementId);
	}

	public void allocateForRun(long runId, List<Need> needs) {
		allocations.allocateForRun(runId, needs);
	}

	public void syncRunAllocation(long runId, String runStatus, List<Need> needs, List<Selection> selections) {
		allocations.syncRunAllocation(runId, runStatus, needs, selections);
	}

	public void consumeForRun(long runId, List<Need> needs, long userId) {
		allocations.consumeForRun(runId, needs, userId);
	}

	public void assertAllocationStillAvailable(long runId, List<Need> needs) {
		allocations.assertAllocationStillAvailable(runId, needs);
	}

	public long availableQuantity(long itemId) {
		return allocations.availableQuantity(itemId);
	}

}
