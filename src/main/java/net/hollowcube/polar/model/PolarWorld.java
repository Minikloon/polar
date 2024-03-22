package net.hollowcube.polar.model;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.hollowcube.polar.CompressionType;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A Java type representing the latest version of the world format.
 */
@SuppressWarnings("UnstableApiUsage")
public class PolarWorld {
    public static final short LATEST_VERSION = 5;

    public static final short VERSION_UNIFIED_LIGHT = 1;
    public static final short VERSION_USERDATA_OPT_BLOCK_ENT_NBT = 2;
    public static final short VERSION_MINESTOM_NBT_READ_BREAK = 3;
    public static final short VERSION_WORLD_USERDATA = 4;
    public static final short VERSION_SHORT_GRASS = 5; // >:(

    public static CompressionType DEFAULT_COMPRESSION = CompressionType.ZSTD;

    // Polar metadata
    private final short version;
    private CompressionType compression;

    // World metadata
    private final byte minSection;
    private final byte maxSection;

    // Chunk data
    private final Long2ObjectMap<PolarChunk> chunks = new Long2ObjectOpenHashMap<>();
    private final ReentrantReadWriteLock chunksLock = new ReentrantReadWriteLock();

    public PolarWorld() {
        this(LATEST_VERSION, DEFAULT_COMPRESSION, (byte) -4, (byte) 19, List.of());
    }

    public PolarWorld(
            short version,
            @NotNull CompressionType compression,
            byte minSection, byte maxSection,
            @NotNull List<PolarChunk> chunks
    ) {
        this.version = version;
        this.compression = compression;

        this.minSection = minSection;
        this.maxSection = maxSection;

        for (var chunk : chunks) {
            var index = ChunkUtils.getChunkIndex(chunk.x(), chunk.z());
            this.chunks.put(index, chunk);
        }
    }

    public short version() {
        return version;
    }

    public @NotNull CompressionType compression() {
        return compression;
    }
    public void setCompression(@NotNull CompressionType compression) {
        this.compression = compression;
    }

    public byte minSection() {
        return minSection;
    }

    public byte maxSection() {
        return maxSection;
    }

    public @Nullable PolarChunk chunkAt(int x, int z) {
        chunksLock.readLock().lock();
        try {
            return chunks.getOrDefault(ChunkUtils.getChunkIndex(x, z), null);
        } finally {
            chunksLock.readLock().unlock();
        }
    }
    public void updateChunkAt(int x, int z, @NotNull PolarChunk chunk) {
        chunksLock.writeLock().lock();
        try {
            chunks.put(ChunkUtils.getChunkIndex(x, z), chunk);
        } finally {
            chunksLock.writeLock().unlock();
        }
    }

    public @NotNull Collection<PolarChunk> chunks() {
        chunksLock.readLock().lock();
        try {
            return new ArrayList<>(chunks.values());
        } finally {
            chunksLock.readLock().unlock();
        }
    }
}
