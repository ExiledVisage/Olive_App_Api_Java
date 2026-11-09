package com.olivamore.api.packaging;

import com.olivamore.api.packaging.lot.LotAllocationService;
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

	PackagingAllocations(LotAllocationService allocations) {
		this.allocations = allocations;
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
