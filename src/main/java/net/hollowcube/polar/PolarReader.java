package net.hollowcube.polar;

import com.github.luben.zstd.Zstd;
import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarWorld;
import net.hollowcube.polar.util.PaletteUtil;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.nbt.BinaryTagReader;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static net.minestom.server.network.NetworkBuffer.*;

@SuppressWarnings("UnstableApiUsage")
public class PolarReader {
    private static final boolean FORCE_LEGACY_NBT = Boolean.getBoolean("polar.debug.force-legacy-nbt");
    private static final int MAX_BLOCK_ENTITIES = Integer.MAX_VALUE;
    private static final int MAX_CHUNKS = Integer.MAX_VALUE;
    private static final int MAX_BLOCK_PALETTE_SIZE = 16*16*16;
    private static final int MAX_BIOME_PALETTE_SIZE = 8*8*8;

    private static final NetworkBuffer.Type<byte[]> LIGHT_DATA = NetworkBuffer.FixedRawBytes(2048);
    private static final NetworkBuffer.Type<byte[]> HEIGHTMAP_SLICE = NetworkBuffer.FixedRawBytes(32);

    protected PolarReader() {}

    public @NotNull PolarWorld read(byte[] data) {
        NetworkBuffer buffer = NetworkBuffer.wrap(data, 0, data.length);
        buffer.writeIndex(data.length); // Set write index to end so readableBytes returns remaining bytes

        Integer magicNumber = buffer.read(INT);
        assertThat(magicNumber == PolarFormat.MAGIC_NUMBER, "Invalid magic number");

        short version = buffer.read(SHORT);
        validateVersion(version);

        CompressionType compression = CompressionType.fromId(buffer.read(BYTE));
        assertThat(compression != null, "Invalid compression type");
        Integer compressedDataLength = buffer.read(VAR_INT);

        // Replace the buffer with a "decompressed" version. This is a no-op if compression is NONE.
        buffer = decompressBuffer(buffer, compression, compressedDataLength);

        byte minSection = buffer.read(BYTE), maxSection = buffer.read(BYTE);
        assertThat(minSection < maxSection, "Invalid section range");

        int chunkCount = buffer.read(VAR_INT);
        List<PolarChunk> chunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; ++i) {
            PolarChunk chunk = readChunk(version, buffer, maxSection - minSection + 1);
            chunks.add(chunk);
        }

        return new PolarWorld(version, compression, minSection, maxSection, chunks);
    }

    private @NotNull PolarChunk readChunk(short version, @NotNull NetworkBuffer buffer, int sectionCount) {
        Integer chunkX = buffer.read(VAR_INT);
        Integer chunkZ = buffer.read(VAR_INT);

        PolarSection[] sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = readSection(version, buffer);
        }

        int blockEntityCount = buffer.read(VAR_INT);
        List<PolarChunk.BlockEntity> blockEntities = new ArrayList<>(blockEntityCount);
        for (int i = 0; i < blockEntityCount; ++i) {
            PolarChunk.BlockEntity blockEntity = readBlockEntity(version, buffer);
            blockEntities.add(blockEntity);
        }

        byte[][] heightmaps = new byte[PolarChunk.HEIGHTMAP_BYTE_SIZE][PolarChunk.HEIGHTMAPS.length];
        int heightmapMask = buffer.read(INT);
        for (int i = 0; i < PolarChunk.HEIGHTMAPS.length; i++) {
            if ((heightmapMask & PolarChunk.HEIGHTMAPS[i]) == 0)
                continue;

            heightmaps[i] = buffer.read(HEIGHTMAP_SLICE);
        }

        // Objects
        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT)
            userData = buffer.read(BYTE_ARRAY);

        return new PolarChunk(
                chunkX, chunkZ,
                sections,
                blockEntities,
                heightmaps,
                userData
        );
    }

    private @NotNull PolarSection readSection(short version, @NotNull NetworkBuffer buffer) {
        // If section is empty exit immediately
        if (buffer.read(BOOLEAN)) return new PolarSection();

        String[] blockPalette = buffer.read(STRING.list(MAX_BLOCK_PALETTE_SIZE)).toArray(String[]::new);
        if (version <= PolarWorld.VERSION_SHORT_GRASS) {
            for (int i = 0; i < blockPalette.length; i++) {
                String strippedID = blockPalette[i].split("\\[")[0];
                if (NamespaceID.from(strippedID).path().equals("grass"))
                    blockPalette[i] = "short_grass";
            }
        }
        int[] blockData = null;
        if (blockPalette.length > 1) {
            blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];

            long[] rawBlockData = buffer.read(LONG_ARRAY);
            int bitsPerEntry = rawBlockData.length * 64 / PolarSection.BLOCK_PALETTE_SIZE;
            PaletteUtil.unpack(blockData, rawBlockData, bitsPerEntry);
        }

        String[] biomePalette = buffer.read(STRING.list(MAX_BIOME_PALETTE_SIZE)).toArray(String[]::new);
        int[] biomeData = null;
        if (biomePalette.length > 1) {
            biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];

            long[] rawBiomeData = buffer.read(LONG_ARRAY);
            int bitsPerEntry = rawBiomeData.length * 64 / PolarSection.BIOME_PALETTE_SIZE;
            PaletteUtil.unpack(biomeData, rawBiomeData, bitsPerEntry);
        }

        byte[] blockLight = null, skyLight = null;

        if (version > PolarWorld.VERSION_UNIFIED_LIGHT) {
            if (buffer.read(BOOLEAN))
                blockLight = buffer.read(LIGHT_DATA);
            if (buffer.read(BOOLEAN))
                skyLight = buffer.read(LIGHT_DATA);
        } else if (buffer.read(BOOLEAN)) {
            blockLight = buffer.read(LIGHT_DATA);
            skyLight = buffer.read(LIGHT_DATA);
        }

        return new PolarSection(blockPalette, blockData, biomePalette, biomeData, blockLight, skyLight);
    }

    private @NotNull PolarChunk.BlockEntity readBlockEntity(int version, @NotNull NetworkBuffer buffer) {
        int posIndex = buffer.read(INT);
        String id = buffer.read(STRING.optional());

        CompoundBinaryTag nbt = null;
        if (version <= PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT || buffer.read(BOOLEAN)) {
            if (version <= PolarWorld.VERSION_MINESTOM_NBT_READ_BREAK || FORCE_LEGACY_NBT) {
                nbt = (CompoundBinaryTag) legacyReadNBT(buffer);
            } else {
                nbt = (CompoundBinaryTag) buffer.read(NBT);
            }
        }

        return new PolarChunk.BlockEntity(
                CoordConversion.chunkBlockIndexGetX(posIndex),
                CoordConversion.chunkBlockIndexGetY(posIndex),
                CoordConversion.chunkBlockIndexGetZ(posIndex),
                id, nbt
        );
    }

    private void validateVersion(int version) {
        String invalidVersionError = String.format("Unsupported Polar version. Up to %d is supported, found %d.",
                PolarWorld.LATEST_VERSION, version);
        assertThat(version <= PolarWorld.LATEST_VERSION, invalidVersionError);
    }

    private @NotNull NetworkBuffer decompressBuffer(@NotNull NetworkBuffer buffer, @NotNull CompressionType compression, int length) {
        return switch (compression) {
            case NONE -> buffer;
            case ZSTD -> {
                byte[] bytes = Zstd.decompress(buffer.read(RAW_BYTES), length);
                NetworkBuffer newBuffer = NetworkBuffer.wrap(bytes, 0, 0);
                newBuffer.writeIndex(bytes.length);
                yield newBuffer;
            }
        };
    }

    /**
     * Minecraft (so Minestom) had a breaking change in NBT reading in 1.20.2. This method replicates the old
     * behavior which we use for any Polar version less than {@link PolarWorld#VERSION_MINESTOM_NBT_READ_BREAK}.
     *
     * @see NetworkBuffer#NBT
     */
    private static BinaryTag legacyReadNBT(@NotNull NetworkBuffer buffer) {
        try {
            var nbtReader = new BinaryTagReader(new DataInputStream(new InputStream() {
                public int read() {
                    return buffer.read(NetworkBuffer.BYTE) & 255;
                }

                public int available() {
                    return (int) buffer.readableBytes();
                }
            }));
            return nbtReader.readNamed().getValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Contract("false, _ -> fail")
    private static void assertThat(boolean condition, @NotNull String message) {
        if (!condition) throw new Error(message);
    }

    public static class Error extends RuntimeException {
        private Error(String message) {
            super(message);
        }
    }

}
