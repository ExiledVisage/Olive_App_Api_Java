package com.olivamore.api.apprelease;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppReleaseControllerTest {

	@Test
	void relativeReleasesPathIsResolvedAgainstWorkingDirectory() {
		// Varsayilan "releases" ve "../..." gibi goreli yollar: uygulama acilmali
		new AppReleaseController("releases");
		new AppReleaseController("../releases/app");
		assertThat(Path.of("releases").toAbsolutePath()).isAbsolute();
	}

	@Test
	void contentDispositionFollowsRfc6266() {
		// ayni basliklar scenarios/app-release.json icinde de
		assertThat(AppReleaseController.disposition("olivamore-1.0.6.apk")).isEqualTo("attachment; filename=olivamore-1.0.6.apk");
		assertThat(AppReleaseController.disposition("olivamore-1.0 beta.apk")).isEqualTo("attachment; filename=\"olivamore-1.0 beta.apk\"");
		assertThat(AppReleaseController.disposition("olivamore-1.0-şık.apk"))
			.isEqualTo("attachment; filename=olivamore-1.0-sik.apk; filename*=utf-8''olivamore-1.0-%C5%9F%C4%B1k.apk");
		// adda "/" varsa hata
		assertThatThrownBy(() -> AppReleaseController.disposition("olivamore-a/b.apk")).isInstanceOf(IllegalArgumentException.class);
	}

}
