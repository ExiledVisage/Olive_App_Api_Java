package com.olivamore.api.support;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Demo veri kopyasinin diskteki onbellegi ({@code target/demo-snapshot/<parmak izi>.bin}). Parmak izi
 * {@code src/main}'in butun dosyalari (migration'lar, tanim verisi, seeder ve onun API'den yurutugu is kurallari) ile
 * seeder parametrelerinin ozetidir: uygulama kodunda herhangi bir degisiklik onbellegi gecersiz kilar. Dosya once
 * gecici adla yazilip tasinir, yarida kalan calisma yarim dosya birakmaz; eski parmak izli dosyalar silinir.
 */
final class DemoSnapshotCache {

	private static final Path DIRECTORY = Path.of("target", "demo-snapshot");
	private static final Path SOURCES = Path.of("src", "main");
	private static final int FORMAT = 1;

	record Snapshot(Map<String, byte[]> tables, Map<String, DemoSnapshot.Sequence> sequences) {
	}

	private final Path file;

	DemoSnapshotCache(Object... parameters) {
		this.file = DIRECTORY.resolve(fingerprint(parameters) + ".bin");
	}

	Optional<Snapshot> read() throws IOException {
		if (!Files.isRegularFile(file)) {
			return Optional.empty();
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
			if (in.readInt() != FORMAT) {
				return Optional.empty();
			}
			Map<String, byte[]> tables = new LinkedHashMap<>();
			for (int i = in.readInt(); i > 0; i--) {
				String name = in.readUTF();
				tables.put(name, in.readNBytes(in.readInt()));
			}
			Map<String, DemoSnapshot.Sequence> sequences = new LinkedHashMap<>();
			for (int i = in.readInt(); i > 0; i--) {
				sequences.put(in.readUTF(), new DemoSnapshot.Sequence(in.readLong(), in.readBoolean()));
			}
			return Optional.of(new Snapshot(tables, sequences));
		}
	}

	void write(Snapshot snapshot) throws IOException {
		Files.createDirectories(DIRECTORY);
		Path temporary = Files.createTempFile(DIRECTORY, "demo-", ".tmp");
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
			out.writeInt(FORMAT);
			out.writeInt(snapshot.tables().size());
			for (Map.Entry<String, byte[]> table : snapshot.tables().entrySet()) {
				out.writeUTF(table.getKey());
				out.writeInt(table.getValue().length);
				out.write(table.getValue());
			}
			out.writeInt(snapshot.sequences().size());
			for (Map.Entry<String, DemoSnapshot.Sequence> sequence : snapshot.sequences().entrySet()) {
				out.writeUTF(sequence.getKey());
				out.writeLong(sequence.getValue().value());
				out.writeBoolean(sequence.getValue().called());
			}
		}
		try {
			Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException e) {
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
		}
		try (Stream<Path> files = Files.list(DIRECTORY)) {
			for (Path old : files.filter(p -> !p.equals(file)).toList()) {
				Files.deleteIfExists(old);
			}
		}
	}

	private static String fingerprint(Object... parameters) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object parameter : parameters) {
				digest.update((parameter + "\n").getBytes(StandardCharsets.UTF_8));
			}
			List<Path> files;
			try (Stream<Path> walk = Files.walk(SOURCES)) {
				files = walk.filter(Files::isRegularFile).sorted().toList();
			}
			for (Path source : files) {
				digest.update(SOURCES.relativize(source).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
				digest.update(Files.readAllBytes(source));
			}
			return HexFormat.of().formatHex(digest.digest(), 0, 16);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
