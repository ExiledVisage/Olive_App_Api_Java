package com.olivamore.api.inventory.material;

import java.util.List;

/**
 * Soy grafi: dugumler, kenarlar; dugum sayisi siniri asildiysa {@code truncated}. Odak tank materyaliyse graf yok,
 * {@code tankLedger} tankin defterine (islem gecmisi, materyale suzulu) isaret eder; degilse null.
 */
record MaterialLineageResponse(String focus, List<Node> nodes, List<Edge> edges, boolean truncated,
	TankLedgerRef tankLedger) {

	record TankLedgerRef(Long storageUnitId, Long materialId) {
	}

	/** Dugum: materyal ya da kosu/depo/siparis. */
	sealed interface Node permits MaterialNode, OtherNode {

		String key();

		int depth();

		Node withDepth(int value);

	}

	/** @param tankEntries tank materyalinde bu agactan yapilan girislerin snapshot'lari; digerlerinde null */
	record MaterialNode(String key, String kind, Long id, String title, String subtitle, Number quantity, String unit,
		String status, String lineageRole, String originType, String batchCode, String deliveryBatchCode, String stageName,
		String storageUnit, List<TankEntrySnapshots.EntrySnapshot> tankEntries, boolean isFocus, int depth) implements Node {

		@Override
		public Node withDepth(int value) {
			return new MaterialNode(key, kind, id, title, subtitle, quantity, unit, status, lineageRole, originType,
				batchCode, deliveryBatchCode, stageName, storageUnit, tankEntries, isFocus, value);
		}

	}

	record OtherNode(String key, String kind, Long id, String title, String subtitle, String status, boolean isFocus,
		int depth) implements Node {

		@Override
		public Node withDepth(int value) {
			return new OtherNode(key, kind, id, title, subtitle, status, isFocus, value);
		}

	}

	record Edge(String from, String to, String label, Number quantity, String unit) {
	}

}
