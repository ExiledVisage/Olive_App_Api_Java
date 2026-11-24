package com.olivamore.api.inventory.storageunit;

import com.olivamore.api.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hacim hesabi elle hesaplanmis degerlerle: ic cap 1000 mm (yaricap 500), silindir 2000 mm. Duz tabanda 1 m sivi
 * pi x 500^2 x 1000 mm3 = 785,40 L.
 */
class TankGeometryTest {

	@Test
	void flatTankIsACylinder() {
		TankGeometry flat = geometry("0", "0", "0", "0");

		assertThat(flat.litresAt(new BigDecimal("1000"))).isEqualByComparingTo("785.40");
		assertThat(flat.litresAt(BigDecimal.ZERO)).isEqualByComparingTo("0");
		assertThat(flat.fullLitres()).isEqualByComparingTo("1570.80");
	}

	@Test
	void bottomConeFillsFromItsTipAndThenTheCylinder() {
		// 45 derece: koni yuksekligi = yaricap = 500 mm; koni hacmi pi x 500^2 x 500 / 3 = 130,90 L
		TankGeometry cone = geometry("45", "0", "0", "0");

		// yarisina kadar: hacim koninin 1/8'i
		assertThat(cone.litresAt(new BigDecimal("250"))).isEqualByComparingTo("16.36");
		assertThat(cone.litresAt(new BigDecimal("500"))).isEqualByComparingTo("130.90");
		assertThat(cone.litresAt(new BigDecimal("1500"))).isEqualByComparingTo("916.30");
	}

	@Test
	void topConeIsAFrustumAndTheTankEndsAtItsTip() {
		TankGeometry top = geometry("0", "45", "0", "0");

		assertThat(top.fullLitres()).isEqualByComparingTo("1701.70");
		assertThat(top.maxReadingMm()).isEqualByComparingTo("2500");
		// ust koninin yarisi: kesik koni pi x 250 / 3 x (500^2 + 500 x 250 + 250^2) = 114,54 L;
		// 1570,80 + 114,54 (yuvarlamadan 1685,333)
		assertThat(top.litresAt(new BigDecimal("2250"))).isEqualByComparingTo("1685.33");
		assertThatThrownBy(() -> top.litresAt(new BigDecimal("2600"))).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void rulerOffsetShiftsTheReadingAndDeadVolumeIsSubtracted() {
		TankGeometry shifted = geometry("0", "0", "100", "5");

		assertThat(shifted.litresAt(new BigDecimal("900"))).isEqualByComparingTo("780.40");
		assertThatThrownBy(() -> geometry("0", "0", "-100", "0").litresAt(new BigDecimal("50")))
			.isInstanceOf(BusinessRuleException.class);
	}

	private static TankGeometry geometry(String bottom, String top, String offset, String dead) {
		return new TankGeometry(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal(bottom),
			new BigDecimal(top), new BigDecimal(offset), new BigDecimal(dead));
	}

}
