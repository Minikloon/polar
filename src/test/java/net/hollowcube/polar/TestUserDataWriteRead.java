package net.hollowcube.polar;

import net.hollowcube.polar.minestom.FilePolarChunkLoader;
import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestUserDataWriteRead {

    static {
        MinecraftServer.init();
    }

    @Test
    void testWriteRead() {
        var world = new PolarWorld();

        var emptySections = new PolarSection[24];
        Arrays.fill(emptySections, new PolarSection());
        world.updateChunkAt(0, 0, new PolarChunk(0, 0, emptySections, List.of(), new byte[0][0], new byte[0]));

        var wa = new UpdateTimeWorldAccess();
        var loader = new FilePolarChunkLoader(world).setWorldAccess(wa);
        var instance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD, loader);
        var chunk = loader.loadChunk(instance, 0, 0).join();

        loader.saveChunk(chunk).join();

        var newPolarChunk = world.chunkAt(0, 0);
        var savedTime = new NetworkBuffer(ByteBuffer.wrap(newPolarChunk.userData())).read(NetworkBuffer.LONG);
        assertEquals(wa.saveTime, savedTime);

        loader.loadChunk(instance, 0, 0).join();
        assertEquals(wa.loadTime, savedTime);
    }

}
