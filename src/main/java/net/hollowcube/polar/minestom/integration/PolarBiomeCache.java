package net.hollowcube.polar.minestom.integration;

import net.minestom.server.MinecraftServer;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PolarBiomeCache {
    private static final Logger logger = LoggerFactory.getLogger(PolarBiomeCache.class);

    private static final DynamicRegistry<Biome> BIOME_REGISTRY = MinecraftServer.getBiomeRegistry();
    public static final int PLAINS_BIOME_ID = BIOME_REGISTRY.getId(Biome.PLAINS);

    private final Map<String, Integer> biomeReadCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> biomeWriteCache = new ConcurrentHashMap<>();

    public int getBiomeId(@NotNull String name) {
        return biomeReadCache.computeIfAbsent(name, n -> {
            int biomeId = BIOME_REGISTRY.getId(computeBiome(name).registry().namespace());
            if (biomeId == -1) {
                logger.error("Failed to find biome: {}", name);
                biomeId = PLAINS_BIOME_ID;
            }
            return biomeId;
        });
    }

    protected Biome computeBiome(@NotNull String name) {
        DynamicRegistry.Key<Biome> biomeKey = DynamicRegistry.Key.of(name);
        Biome biome = BIOME_REGISTRY.get(biomeKey);
        if (biome == null) {
            logger.error("Failed to find biome: {}", name);
            biome = BIOME_REGISTRY.get(Biome.PLAINS);
        }
        return biome;
    }

    public String getBiomeName(int id) {
        return biomeWriteCache.computeIfAbsent(id, this::computeBiomeName);
    }

    protected String computeBiomeName(int id) {
        DynamicRegistry.Key<Biome> biomeKey = BIOME_REGISTRY.getKey(id);
        if (biomeKey == null) {
            logger.error("Failed to find biome: {}", id);
            biomeKey = Biome.PLAINS;
        }
        return biomeKey.name();
    }
}
