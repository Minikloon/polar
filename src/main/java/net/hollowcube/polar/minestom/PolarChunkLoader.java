package net.hollowcube.polar.minestom;

import net.hollowcube.polar.PolarFormat;
import net.hollowcube.polar.minestom.integration.InMemoryPolarWorld;
import net.hollowcube.polar.model.PolarWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.thread.TickThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public abstract class PolarChunkLoader implements IChunkLoader {
    private static final Logger LOG = LoggerFactory.getLogger(PolarChunkLoader.class);

    private Instance instance;
    private CompletableFuture<InMemoryPolarWorld> loadingWorld;

    public abstract CompletableFuture<byte[]> loadWorld();

    public abstract CompletableFuture<Void> saveWorld(byte[] polarBytes);

    public final boolean isLoading() {
        return loadingWorld == null || !loadingWorld.isDone();
    }

    public CompletableFuture<InMemoryPolarWorld> getLoadingFuture() {
        return loadingWorld;
    }

    @Override
    public void loadInstance(@NotNull Instance instance) {
        this.instance = instance;
        this.loadingWorld = loadWorld().thenApplyAsync(bytes -> {
            if (bytes == null) {
                return new InMemoryPolarWorld(new PolarWorld());
            }

            PolarWorld polarWorld = PolarFormat.READER.read(bytes);
            return new InMemoryPolarWorld(polarWorld);
        });
    }

    @Override
    public final @Nullable Chunk loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        if (loadingWorld == null) {
            throw new IllegalStateException("Attempted to load a chunk before loadInstance()!");
        }
        InMemoryPolarWorld inMemory = loadingWorld.join();
        return inMemory.getLoader().loadChunk(instance, chunkX, chunkZ);
    }

    @Override
    public void saveInstance(@NotNull Instance instance) {
        saveChunks(instance.getChunks());
    }

    @Override
    public void saveChunks(@NotNull Collection<Chunk> chunks) {
        if (loadingWorld == null) {
            throw new IllegalStateException("Attempted to save chunks before loadInstance()!");
        }

        InMemoryPolarWorld inMemory = loadingWorld.join();
        CompletableFuture.runAsync(() -> {
            inMemory.getSaver().writeChunksDataToMemory(chunks);
        }, this::sync).join();

        byte[] bytes = inMemory.getSaver().saveChunks();
        saveWorld(bytes);
    }

    @Override
    public final void saveChunk(@NotNull Chunk chunk) {
        saveChunks(List.of(chunk));
    }

    @Override
    public void unloadChunk(Chunk chunk) {
        loadingWorld.thenAcceptAsync(inMemory -> {
            inMemory.getSaver().writeChunksDataToMemory(List.of(chunk));
        }, this::sync);
    }

    private void sync(Runnable runnable) {
        Thread currentThread = Thread.currentThread();
        if (currentThread instanceof TickThread || MinecraftServer.isStopping()) {
            runnable.run();
        } else {
            instance.scheduler().scheduleNextTick(runnable);
        }
    }

    @Override
    public boolean supportsParallelSaving() {
        return true;
    }
}