package net.hollowcube.polar.minestom.integration;

import net.hollowcube.polar.model.PolarWorld;

public class InMemoryPolarWorld {
    private final PolarWorld polarWorld;

    private final PolarBiomeCache biomeCache;

    private final MinestomPolarLoader loader;
    private final MinestomPolarSaver saver;

    public InMemoryPolarWorld(PolarWorld polarWorld) {
        this.polarWorld = polarWorld;

        this.biomeCache = new PolarBiomeCache();

        this.loader = new MinestomPolarLoader(polarWorld, biomeCache);
        this.saver = new MinestomPolarSaver(polarWorld, biomeCache);
    }

    public MinestomPolarLoader getLoader() {
        return loader;
    }

    public MinestomPolarSaver getSaver() {
        return saver;
    }
}
