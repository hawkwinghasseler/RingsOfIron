package com.ringsofiron.coreengine;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Writes a file whole or not at all: the text goes to a temporary file beside it, which is flushed
 * to disk and then renamed over the old one. A crash, a full disk or a kill part way through leaves
 * the old file as it was (and at worst a stray {@code .tmp} beside it), never half a save.
 */
public final class AtomicFiles {
    /** Times to retry the rename when Windows has the old file open (a virus scan, the indexer). */
    private static final int RENAME_TRIES = 5;

    private AtomicFiles() {
    }

    public static void writeString(FileHandle file, String text) {
        writeBytes(file, text.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeBytes(FileHandle file, byte[] bytes) {
        if (file.type() == FileType.Classpath || file.type() == FileType.Internal) {
            throw new GdxRuntimeException("Cannot write to a " + file.type() + " file: " + file);
        }
        Path target = file.file().toPath().toAbsolutePath();
        Path dir = target.getParent();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, target.getFileName().toString() + ".", ".tmp");
            try (FileChannel out = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            replace(tmp, target);
            tmp = null;
        } catch (IOException e) {
            throw new GdxRuntimeException("Error writing file: " + file, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // Left behind; it ends in .tmp, so nothing reads it as a save
                }
            }
        }
    }

    private static void replace(Path from, Path to) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                try {
                    Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (AccessDeniedException e) {
                if (attempt >= RENAME_TRIES) throw e;
                try {
                    Thread.sleep(20L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
