package net.hollowcube.polar.minestom.integration;

import net.hollowcube.polar.minestom.FilePolarChunkLoader;
import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentBlockState;
import net.minestom.server.command.builder.exception.ArgumentSyntaxException;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinestomPolarLoader {
    private static final Logger logger = LoggerFactory.getLogger(FilePolarChunkLoader.class);

    private static final BlockManager BLOCK_MANAGER = MinecraftServer.getBlockManager();

    private final PolarWorld polarWorld;
    private final PolarBiomeCache biomeCache;

    public MinestomPolarLoader(PolarWorld polarWorld, PolarBiomeCache biomeCache) {
        this.polarWorld = polarWorld;
        this.biomeCache = biomeCache;
    }

    public final @Nullable Chunk loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        // Only need to lock for this tiny part, chunks are immutable.
        var chunkData = polarWorld.chunkAt(chunkX, chunkZ);
        if (chunkData == null) return null;

        // We are making the assumption here that the chunk height is the same as this world.
        // Polar includes world height metadata in the prelude and assumes all chunks match
        // those values. We check that the dimension settings match in #loadInstance, so
        // here it can be ignored/assumed.

        // Load the chunk
        var chunk = instance.getChunkSupplier().createChunk(instance, chunkX, chunkZ);

        int sectionY = chunk.getMinSection();
        for (var sectionData : chunkData.sections()) {
            if (sectionData.isEmpty()) {
                sectionY++;
                continue;
            }

            var section = chunk.getSection(sectionY);
            loadSection(sectionData, section);
            sectionY++;
        }

        for (var blockEntity : chunkData.blockEntities()) {
            loadBlockEntity(blockEntity, chunk);
        }

        return chunk;
    }

    private void loadSection(@NotNull PolarSection sectionData, @NotNull Section section) {
        // assumed that section is _not_ empty

        // Blocks
        var rawBlockPalette = sectionData.blockPalette();
        var blockPalette = new Block[rawBlockPalette.length];
        for (int i = 0; i < rawBlockPalette.length; i++) {
            try {
                //noinspection deprecation
                blockPalette[i] = ArgumentBlockState.staticParse(rawBlockPalette[i]);
            } catch (ArgumentSyntaxException e) {
                logger.error("Failed to parse block state: {} ({})", rawBlockPalette[i], e.getMessage());
                blockPalette[i] = Block.AIR;
            }
        }
        if (blockPalette.length == 1) {
            section.blockPalette().fill(blockPalette[0].stateId());
        } else {
            final var paletteData = sectionData.blockData();
            section.blockPalette().setAll((x, y, z) -> {
                int index = y * Chunk.CHUNK_SECTION_SIZE * Chunk.CHUNK_SECTION_SIZE + z * Chunk.CHUNK_SECTION_SIZE + x;
                return blockPalette[paletteData[index]].stateId();
            });
        }

        // Biomes
        var rawBiomePalette = sectionData.biomePalette();
        var biomePalette = new int[rawBiomePalette.length];
        for (int i = 0; i < rawBiomePalette.length; i++) {
            biomePalette[i] = biomeCache.getBiomeId(rawBiomePalette[i]);
        }
        if (biomePalette.length == 1) {
            section.biomePalette().fill(biomePalette[0]);
        } else {
            final var paletteData = sectionData.biomeData();
            section.biomePalette().setAll((x, y, z) -> {
                int index = x / 4 + (z / 4) * 4 + (y / 4) * 16;

                var paletteIndex = paletteData[index];
                if (paletteIndex >= biomePalette.length) {
                    logger.error("Invalid biome palette index. This is probably a corrupted world, " +
                            "but it has been loaded with plains instead. No data has been written.");
                    return PolarBiomeCache.PLAINS_BIOME_ID;
                }

                return biomePalette[paletteIndex];
            });
        }

        // Light
        if (sectionData.hasBlockLightData()) {
            section.setBlockLight(sectionData.blockLight());
        }
        if (sectionData.hasSkyLightData()) {
            section.setSkyLight(sectionData.skyLight());
        }
    }

    private void loadBlockEntity(@NotNull PolarChunk.BlockEntity blockEntity, @NotNull Chunk chunk) {
        // Fetch the block type, we can ignore Handler/NBT since we are about to replace it
        var block = chunk.getBlock(blockEntity.x(), blockEntity.y(), blockEntity.z(), Block.Getter.Condition.TYPE);

        if (blockEntity.id() != null)
            block = block.withHandler(BLOCK_MANAGER.getHandlerOrDummy(blockEntity.id()));
        if (blockEntity.data() != null)
            block = block.withNbt(blockEntity.data());

        chunk.setBlock(blockEntity.x(), blockEntity.y(), blockEntity.z(), block);
    }
}
