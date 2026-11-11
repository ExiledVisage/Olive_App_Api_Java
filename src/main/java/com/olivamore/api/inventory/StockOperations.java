package com.olivamore.api.inventory;

import com.olivamore.api.inventory.material.Material;
import com.olivamore.api.inventory.material.MaterialAttributeValue;
import com.olivamore.api.inventory.material.MaterialAttributeValueRepository;
import com.olivamore.api.inventory.material.MaterialRepository;
import com.olivamore.api.inventory.material.MaterialUnit;
import com.olivamore.api.inventory.storagemovement.StorageMovementService;
import com.olivamore.api.shared.error.ModelNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Stok hareketlerinin diger modullere acik kismi: uretim asamasinin (depo hareketi, on dinlendirme) yaptigi tank
 * girisi, cikisi ve transferi; dolum kosusunun tanktan cekisi; uretim isleyicilerinin (tartim, donusum, analiz)
 * materyal yazimlari. Kurallar ve bakiye {@code StorageMovementService}'te, tank sayfasindakiyle ayni. Proses
 * hareketleri {@code BATCH_PROCESS} kaynakli yazilir, bolunen materyal prosese baglanir; dolum kosusunun cikisi
 * {@code MANUAL} kaynakli ve kosuya bagli. Materyal yazimlari ayni islemde SQL ile okundugu icin hemen islenir.
 */
@Component
public class StockOperations {

	/** Analizle yazilan attribute degeri (tek tanim icin tek satir). */
	public record AttributeValue(Boolean valueBoolean, Long valueInteger, BigDecimal valueDecimal, String valueText,
		JsonNode valueJson) {
	}

	private final StorageMovementService movements;
	private final MaterialRepository materials;
	private final MaterialAttributeValueRepository attributeValues;

	@PersistenceContext
	private EntityManager entityManager;

	StockOperations(StorageMovementService movements, MaterialRepository materials,
		MaterialAttributeValueRepository attributeValues) {
		this.movements = movements;
		this.materials = materials;
		this.attributeValues = attributeValues;
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

	/** On dinlendirme asamasinin girisi: partideki materyal (ya da bir kismi) on dinlendirme tankina alinir. */
	public void enterPreSettling(long unitId, long materialId, BigDecimal quantity, long batchProcessId, String note) {
		movements.enterPreSettling(unitId, materialId, quantity, batchProcessId, note);
	}

	/** Dinlenmedeki asamanin iptali: prosesin tankta duran materyalleri partiye doner. */
	public void cancelPreSettling(long batchProcessId, String note) {
		movements.cancelPreSettling(batchProcessId, note);
	}

	/** Uretim prosesinin yarattigi, partinin icindeki materyal. @return materyal id */
	@Transactional
	public long createBatchMaterial(long batchId, long typeId, Long parentId, BigDecimal quantity, String unit,
		long stageId, long processId, String note) {
		long id = materials.save(Material.ofBatchProcess(batchId, typeId, parentId, quantity, MaterialUnit.of(unit), stageId,
			processId, note)).getId();
		entityManager.flush();
		return id;
	}

	/** Tartimda olculen miktar kaydedilir. */
	@Transactional
	public void recordWeighing(long materialId, BigDecimal quantity, String unit, long stageId, long batchId,
		String processNote) {
		material(materialId).recordWeighing(quantity, MaterialUnit.of(unit), stageId, batchId, processNote);
		entityManager.flush();
	}

	/** Donusum girdisinin kullanilan miktari dusulur; kalan yoksa proses tarafindan tuketilir. */
	@Transactional
	public void consumeInput(long materialId, BigDecimal used, long processId, String processNote) {
		material(materialId).consumeInput(used, processId, processNote);
		entityManager.flush();
	}

	/** Analiz degeri: tanim icin satir varsa degisir, yoksa eklenir. */
	@Transactional
	public void recordAttribute(long materialId, long definitionId, AttributeValue value) {
		attributeValues.findByMaterialIdAndDefinitionId(materialId, definitionId).ifPresentOrElse(
			existing -> existing.update(value.valueBoolean(), value.valueInteger(), value.valueDecimal(), value.valueText(),
				value.valueJson()),
			() -> attributeValues.save(new MaterialAttributeValue(materialId, definitionId, value.valueBoolean(),
				value.valueInteger(), value.valueDecimal(), value.valueText(), value.valueJson())));
		entityManager.flush();
	}

	private Material material(long id) {
		return materials.findById(id).orElseThrow(() -> new ModelNotFoundException("Material", id));
	}

}
