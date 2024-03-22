# Polar (Kloon Edition)

Read: https://github.com/hollow-cube/polar

This branch remixes the code to my taste for my own use.

Main benefit is that it's easy to create new Minestom ChunkLoaders:

    public class FilePolarChunkLoader extends PolarChunkLoader {
        private final Path path;
    
        public FilePolarChunkLoader(@NotNull Path path) {
            this.path = path;
        }
    
        @Override
        public CompletableFuture<byte[]> loadWorld() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (Files.exists(path)) {
                        return Files.readAllBytes(path);
                    } else {
                        return null; // empty world that will go through Minestom's generator
                    }
                } catch (Throwable t) {
                    throw new RuntimeException("Error loading world", t);
                }
            });
        }
    
        @Override
        public CompletableFuture<Void> saveWorld(byte[] polarBytes) {
            return CompletableFuture.runAsync(() -> {
                try {
                    Files.write(path, polarBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to save world", t);
                }
            });
        }
    }