package net.hollowcube.polar.minestom;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public class FilePolarChunkLoader extends PolarChunkLoader {
    private final Path path;

    public FilePolarChunkLoader(@NotNull Path path) {
        this.path = path;

    }

    @Override
    public CompletableFuture<byte[]> loadWorld() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.exists(path)) {
                    return Files.readAllBytes(path);
                } else {
                    return null;
                }
            } catch (Throwable t) {
                throw new RuntimeException("Error loading world", t);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveWorld(byte[] polarBytes) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.write(path, polarBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to save world", t);
            }
        });
    }
}
