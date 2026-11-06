package com.olivamore.api.shared.persistence;

import com.olivamore.api.shared.error.BusinessRuleException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Silme oncesi referans kontrolu. Laravel'deki {@code GuardsDeletion} trait'inin karsiligi.
 * <p>
 * FK restrict olan tablolarda kontrolsuz silme 500 doner; cascade olanlarda
 * gecmis kayitlar sessizce kaybolur. Kullanildigi her yer adiyla sayilir:
 * <pre>
 * DeletionGuard.of("Bu ürün tipi")
 *     .usedBy("ürün", productCount)
 *     .usedBy("parti", batchCount)
 *     .check();
 * // -> 422 "Bu ürün tipi silinemez; kullanıldığı yerler: 3 ürün, 2 parti."
 * </pre>
 */
public final class DeletionGuard {

	private final String subject;
	private final Map<String, Long> usages = new LinkedHashMap<>();

	private DeletionGuard(String subject) {
		this.subject = subject;
	}

	public static DeletionGuard of(String subject) {
		return new DeletionGuard(subject);
	}

	/** @param label Laravel'deki etiketin aynisi, ornegin "üretim partisi" */
	public DeletionGuard usedBy(String label, long count) {
		usages.put(label, count);
		return this;
	}

	public void check() {
		check(null);
	}

	/** @param hint mesajin sonuna eklenen aciklama, ornegin "Önce pasife alın." */
	public void check(String hint) {
		List<String> parts = new ArrayList<>();
		usages.forEach((label, count) -> {
			if (count > 0) {
				parts.add(count + " " + label);
			}
		});
		if (parts.isEmpty()) {
			return;
		}
		throw new BusinessRuleException(subject + " silinemez; kullanıldığı yerler: " + String.join(", ", parts) + "."
			+ (hint != null && !hint.isEmpty() ? " " + hint : ""));
	}

}
