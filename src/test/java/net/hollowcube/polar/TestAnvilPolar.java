package net.hollowcube.polar;

import net.hollowcube.polar.anvil.AnvilPolar;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAnvilPolar {

    @Test
    void testConvertAnvilWorld() throws Exception {
        var world = AnvilPolar.anvilToPolar(
                Path.of("./src/test/resources/bench").toRealPath(),
                -4, 19
        );
        assertEquals(-4, world.minSection());

        PolarWriter writer = new PolarWriter();
        var result = writer.write(world);
        System.out.println(result.length);
        Files.write(Path.of("./src/test/resources/5.polar"), result);
    }

}
