package net.hollowcube.polar.minestom.integration;

import net.minestom.server.MinecraftServer;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.biomes.Biome;
import net.minestom.server.world.biomes.BiomeManager;
import net.minestom.server.world.biomes.VanillaBiome;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PolarBiomeCache {
    private static final Logger logger = LoggerFactory.getLogger(PolarBiomeCache.class);

    private static final BiomeManager BIOME_MANAGER = MinecraftServer.getBiomeManager();
    public static final int PLAINS_BIOME_ID = BIOME_MANAGER.getId(VanillaBiome.PLAINS);

    private final Map<String, Integer> biomeReadCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> biomeWriteCache = new ConcurrentHashMap<>();

    public int getBiomeId(@NotNull String name) {
        return biomeReadCache.computeIfAbsent(name, n -> {
            int biomeId = BIOME_MANAGER.getId(computeBiome(name));
            if (biomeId == -1) {
                logger.error("Failed to find biome: {}", name);
                biomeId = PLAINS_BIOME_ID;
            }
            return biomeId;
        });
    }

    protected Biome computeBiome(@NotNull String name) {
        var biome = MinecraftServer.getBiomeManager().getByName(NamespaceID.from(name));
        if (biome == null) {
            logger.error("Failed to find biome: {}", name);
            biome = VanillaBiome.PLAINS;
        }
        return biome;
    }

    public String getBiomeName(int id) {
        return biomeWriteCache.computeIfAbsent(id, this::computeBiomeName);
    }

    protected String computeBiomeName(int id) {
        var biome = MinecraftServer.getBiomeManager().getById(id);
        if (biome == null) {
            logger.error("Failed to find biome: {}", id);
            biome = VanillaBiome.PLAINS;
        }
        return biome.name();
    }
}
