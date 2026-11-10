package com.olivamore.api.apprelease;

/** Yayinlanmis surum; {@code notes}, {@code sha256} ve {@code publishedAt} dosyada nasil yaziliysa oyle doner. */
record AppVersionResponse(long versionCode, String versionName, Object notes, Object sha256, long size,
	Object publishedAt, String downloadUrl) {
}
