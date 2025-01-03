package net.hollowcube.polar;

import com.github.luben.zstd.Zstd;
import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarWorld;
import net.hollowcube.polar.util.PaletteUtil;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.Chunk;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import static net.minestom.server.network.NetworkBuffer.*;

@SuppressWarnings("UnstableApiUsage")
public class PolarWriter {
    protected PolarWriter() {}

    public byte[] write(@NotNull PolarWorld world) {
        // Write the compressed content first
        byte[] contentBytes = makeArray(content -> {
            content.write(BYTE, world.minSection());
            content.write(BYTE, world.maxSection());

            Collection<PolarChunk> chunks = world.chunks();
            content.write(VAR_INT, chunks.size());
            for (PolarChunk chunk : chunks) {
                writeChunk(content, chunk);
            }
        });

        // Create final buffer
        return NetworkBuffer.makeArray(buffer -> {
            buffer.write(INT, PolarFormat.MAGIC_NUMBER);
            buffer.write(SHORT, PolarWorld.LATEST_VERSION);
            buffer.write(BYTE, (byte) world.compression().ordinal());
            switch (world.compression()) {
                case NONE -> {
                    buffer.write(VAR_INT, contentBytes.length);
                    buffer.write(RAW_BYTES, contentBytes);
                }
                case ZSTD -> {
                    buffer.write(VAR_INT, contentBytes.length);
                    buffer.write(RAW_BYTES, Zstd.compress(contentBytes));
                }
            }
        });
    }

    private void writeChunk(@NotNull NetworkBuffer buffer, @NotNull PolarChunk chunk) {
        buffer.write(VAR_INT, chunk.x());
        buffer.write(VAR_INT, chunk.z());

        for (PolarSection section : chunk.sections()) {
            writeSection(buffer, section);
        }

        buffer.write(VAR_INT, chunk.blockEntities().size());
        for (PolarChunk.BlockEntity blockEntity : chunk.blockEntities()) {
            writeBlockEntity(buffer, blockEntity);
        }

        //todo heightmaps
        buffer.write(INT, PolarChunk.HEIGHTMAP_NONE);

        buffer.write(BYTE_ARRAY, chunk.userData());
    }

    private void writeSection(@NotNull NetworkBuffer buffer, @NotNull PolarSection section) {
        buffer.write(BOOLEAN, section.isEmpty());
        if (section.isEmpty()) return;

        // Blocks
        String[] blockPalette = section.blockPalette();
        buffer.write(STRING.list(), Arrays.asList(blockPalette));
        if (blockPalette.length > 1) {
            int[] blockData = section.blockData();
            int bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            buffer.write(LONG_ARRAY, PaletteUtil.pack(blockData, bitsPerEntry));
        }

        // Biomes
        String[] biomePalette = section.biomePalette();
        buffer.write(STRING.list(), Arrays.asList(biomePalette));
        if (biomePalette.length > 1) {
            int[] biomeData = section.biomeData();
            int bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            buffer.write(LONG_ARRAY, PaletteUtil.pack(biomeData, bitsPerEntry));
        }

        // Light
        buffer.write(BOOLEAN, section.hasBlockLightData());
        if (section.hasBlockLightData())
            buffer.write(RAW_BYTES, section.blockLight());
        buffer.write(BOOLEAN, section.hasSkyLightData());
        if (section.hasSkyLightData())
            buffer.write(RAW_BYTES, section.skyLight());
    }

    private void writeBlockEntity(@NotNull NetworkBuffer buffer, @NotNull PolarChunk.BlockEntity blockEntity) {
        int index = CoordConversion.chunkBlockIndex(blockEntity.x(), blockEntity.y(), blockEntity.z());
        buffer.write(INT, index);
        buffer.write(STRING.optional(), blockEntity.id());
        buffer.write(NBT.optional(), blockEntity.data());
    }
}
