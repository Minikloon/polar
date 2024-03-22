package net.hollowcube.polar;

import net.hollowcube.polar.model.PolarChunk;
import net.hollowcube.polar.model.PolarSection;
import net.hollowcube.polar.model.PolarWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class TestReaderBackwardsCompatibility {

    @Test
    void testVersion1() {
        runTest(1);
    }

    @Test
    void testVersion2() {
        runTest(2);
    }

    @Test
    void testVersion3() {
        runTest(3);
    }

    @Test
    void testVersion4() {
        runTest(4);
    }

    @Test
    void testVersion5() {
        runTest(5);
    }

    private static void runTest(int version) {
        InputStream is = TestReaderBackwardsCompatibility.class.getResourceAsStream("/backward/" + version + ".polar");
        assertNotNull(is);

        byte[] worldData = assertDoesNotThrow(is::readAllBytes);
        PolarWorld world = assertDoesNotThrow(() -> PolarFormat.READER.read(worldData));
        assertNotNull(world);

        assertEquals(32 * 32, world.chunks().size());

        PolarChunk chunk = world.chunkAt(5, 5);
        assertNotNull(chunk);
        assertEquals(0, chunk.blockEntities().size());

        PolarSection section = chunk.sections()[7];
        String[] expectedPalette = new String[]{"granite", "stone", "diorite", "gravel", "coal_ore", "copper_ore", "iron_ore", "dirt"};
        assertArrayEquals(expectedPalette, section.blockPalette());
    }

}
