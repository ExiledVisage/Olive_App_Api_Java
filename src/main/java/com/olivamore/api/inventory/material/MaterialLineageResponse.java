package com.olivamore.api.inventory.material;

import java.util.List;

/** Soy grafi: dugumler, kenarlar; dugum sayisi siniri asildiysa {@code truncated}. */
record MaterialLineageResponse(String focus, List<Node> nodes, List<Edge> edges, boolean truncated) {

	/** Dugum: materyal ya da kosu/depo/siparis. */
	sealed interface Node permits MaterialNode, OtherNode {

		String key();

		int depth();

		Node withDepth(int value);

	}

	record MaterialNode(String key, String kind, Long id, String title, String subtitle, Number quantity, String unit,
		String status, String lineageRole, String originType, String batchCode, String stageName, String storageUnit,
		boolean isFocus, int depth) implements Node {

		@Override
		public Node withDepth(int value) {
			return new MaterialNode(key, kind, id, title, subtitle, quantity, unit, status, lineageRole, originType,
				batchCode, stageName, storageUnit, isFocus, value);
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
