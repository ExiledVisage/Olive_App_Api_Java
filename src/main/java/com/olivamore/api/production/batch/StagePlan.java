package com.olivamore.api.production.batch;

import com.olivamore.api.productionline.ProductionLineResources.Stage;

import java.util.List;

/**
 * Hattin asamalari bolme noktasinda ikiye: ilk makineli asamadan oncesi teslimatta, o ve sonrasi grupta. Makineli
 * asamasi olmayan hatta hepsi teslimatta, bolme yok.
 */
record StagePlan(List<Stage> delivery, List<Stage> group) {

	static StagePlan of(List<Stage> stages) {
		for (int i = 0; i < stages.size(); i++) {
			if (stages.get(i).usesMachine()) {
				return new StagePlan(stages.subList(0, i), stages.subList(i, stages.size()));
			}
		}
		return new StagePlan(stages, List.of());
	}

	boolean splittable() {
		return !group.isEmpty();
	}

	static String label(Stage stage) {
		return stage.name() != null ? stage.name() : "#" + stage.id();
	}

}
