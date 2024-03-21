package net.hollowcube.polar;

import net.hollowcube.polar.model.PolarWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPolarReader {

    @Test
    void testReadInvalidMagic() {
        var e = assertThrows(PolarReader.Error.class, () -> {
            PolarFormat.READER.read(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        });
        assertEquals("Invalid magic number", e.getMessage());
    }

    @Test
    void testNewerVersionFail() {
        var e = assertThrows(PolarReader.Error.class, () -> {
            PolarFormat.READER.read(new byte[]{
                    0x50, 0x6F, 0x6C, 0x72, // magic number
                    0x50, 0x50, // version
            });
        });
        assertEquals("Unsupported Polar version. Up to " + PolarWorld.LATEST_VERSION + " is supported, found 20560.", e.getMessage());
    }

}
