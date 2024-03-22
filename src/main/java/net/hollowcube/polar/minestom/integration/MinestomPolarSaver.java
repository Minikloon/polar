package net.hollowcube.polar.minestom.integration;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.hollowcube.polar.PolarFormat;
import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarWorld;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class MinestomPolarSaver {
    private final PolarWorld polarWorld;
    private final PolarBiomeCache biomeCache;

    public MinestomPolarSaver(PolarWorld polarWorld, PolarBiomeCache biomeCache) {
        this.polarWorld = polarWorld;
        this.biomeCache = biomeCache;
    }

    public void writeChunksDataToMemory(@NotNull Collection<Chunk> chunks) {
        var blockCache = new Short2ObjectOpenHashMap<String>();

        // Update state of each chunk locally
        chunks.forEach(c -> updateChunkData(blockCache, c));
    }

    public byte[] saveChunks() {
        return PolarFormat.WRITER.write(polarWorld);
    }

    private void updateChunkData(@NotNull Short2ObjectMap<String> blockCache, @NotNull Chunk chunk) {
        var dimension = chunk.getInstance().getDimensionType();

        var blockEntities = new ArrayList<PolarChunk.BlockEntity>();
        var sections = new PolarSection[dimension.getHeight() / Chunk.CHUNK_SECTION_SIZE];
        assert sections.length == chunk.getSections().size(): "World height mismatch";

        var heightmaps = new byte[32][PolarChunk.HEIGHTMAPS.length];

        var userData = new byte[0];

        synchronized (chunk) {
            for (int i = 0; i < sections.length; i++) {
                int sectionY = i + chunk.getMinSection();
                var section = chunk.getSection(sectionY);
                //todo check if section is empty and skip

                var blockPalette = new ArrayList<String>();
                int[] blockData = null;
                if (section.blockPalette().count() == 0) {
                    // Short circuit empty palette
                    blockPalette.add("air");
                } else {
                    var localBlockData = new int[PolarSection.BLOCK_PALETTE_SIZE];

                    section.blockPalette().getAll((x, sectionLocalY, z, blockStateId) -> {
                        final int blockIndex = x + sectionLocalY * 16 * 16 + z * 16;

                        // Section palette
                        var namespace = blockCache.computeIfAbsent((short) blockStateId, unused -> blockToString(Block.fromStateId((short) blockStateId)));
                        int paletteId = blockPalette.indexOf(namespace);
                        if (paletteId == -1) {
                            paletteId = blockPalette.size();
                            blockPalette.add(namespace);
                        }
                        localBlockData[blockIndex] = paletteId;
                    });

                    blockData = localBlockData;

                    // Block entities
                    for (int sectionLocalY = 0; sectionLocalY < Chunk.CHUNK_SECTION_SIZE; sectionLocalY++) {
                        for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                            for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                                int y = sectionLocalY + sectionY * Chunk.CHUNK_SECTION_SIZE;
                                var block = chunk.getBlock(x, y, z, Block.Getter.Condition.CACHED);
                                if (block == null) continue;

                                var handlerId = block.handler() == null ? null : block.handler().getNamespaceId().asString();
                                if (handlerId != null || block.hasNbt()) {
                                    blockEntities.add(new PolarChunk.BlockEntity(
                                            x, y, z, handlerId, block.nbt()
                                    ));
                                }
                            }
                        }
                    }
                }

                var biomePalette = new ArrayList<String>();
                var biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];

                section.biomePalette().getAll((x, y, z, id) -> {
                    var biomeId = biomeCache.getBiomeName(id);

                    var paletteId = biomePalette.indexOf(biomeId);
                    if (paletteId == -1) {
                        paletteId = biomePalette.size();
                        biomePalette.add(biomeId);
                    }

                    biomeData[x + z * 4 + y * 4 * 4] = paletteId;
                });

                byte[] blockLight = section.blockLight().array();
                if (blockLight.length != 2048) blockLight = null;
                byte[] skyLight = section.skyLight().array();
                if (skyLight.length != 2048) skyLight = null;

                sections[i] = new PolarSection(
                        blockPalette.toArray(new String[0]), blockData,
                        biomePalette.toArray(new String[0]), biomeData,
                        blockLight, skyLight
                );
            }

            //todo heightmaps
        }

        polarWorld.updateChunkAt(
                chunk.getChunkX(),
                chunk.getChunkZ(),
                new PolarChunk(
                        chunk.getChunkX(),
                        chunk.getChunkZ(),
                        sections,
                        blockEntities,
                        heightmaps,
                        userData
                )
        );
    }

    private @NotNull String blockToString(@NotNull Block block) {
        var builder = new StringBuilder(block.name());
        if (block.properties().isEmpty()) return builder.toString();

        builder.append('[');
        for (var entry : block.properties().entrySet()) {
            builder.append(entry.getKey())
                    .append('=')
                    .append(entry.getValue())
                    .append(',');
        }
        builder.deleteCharAt(builder.length() - 1);
        builder.append(']');

        return builder.toString();
    }
}
