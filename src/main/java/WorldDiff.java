import config.Config;
import game.data.WorldManager;
import game.data.chunk.Chunk;
import game.data.chunk.ChunkSection;
import game.data.chunk.palette.Palette;
import game.data.chunk.version.ChunkSection_1_18;
import game.data.chunk.version.Chunk_1_17;
import game.data.chunk.version.Chunk_1_18;
import game.data.coordinates.Coordinate2D;
import game.data.coordinates.Coordinate3D;
import game.data.coordinates.CoordinateDim2D;
import game.data.dimension.Dimension;
import game.data.region.McaFile;
import se.llbit.nbt.SpecificTag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class WorldDiff {
    private static Mode mode;


    /**
     * DEL means blocks that have been removed are left. block1 == block2 -> set to air
     * STAY means blocks that stayed the same are left. block1 != block2 -> set to air
     * ADD means blocks that have been added are left. block1 == block2 -> set to air, but the world input order is switched.
     */
    private enum Mode {
        ADD,
        DEL,
        STAY
    }

    public static void main(String... args) throws IOException {
        if (args == null || args.length < 4) {
            System.err.println("usage: <world1> <world2> <output> <mode(ADD, DEL, STAY)>");
            System.out.println("oldest world should be specified first.");
            System.exit(1);
        }
        mode = getMode(args[3]);

        String world1 = args[0];
        String world2;
        if (mode.equals(Mode.ADD)) {
            world1 = args[1];
            world2 = args[0];
        } else {
            world2 = args[1];
        }
        final String output = args[2];
        Files.createDirectories(Path.of(output + "/region/"));

        final Set<String> alreadyConverted = new HashSet<>(Arrays.asList(new File(output + "/region/").list()));
        setupWorld();

        Arrays.stream(new File(world1 + "/region/").listFiles())
                .parallel()
                .filter(file -> !alreadyConverted.contains(file.getName()))
                .filter(file -> file.getName().endsWith(".mca"))
                .forEach(file -> {
                    try {
                        McaFile r2;
                        try {
                            r2 = new McaFile(new File(String.format("%s/region/%s", world2, file.getName())));
                        } catch (IOException e) {
                            System.out.println("Region doesn't exist");
                            return;
                        }
                        System.out.println(file.getAbsolutePath());
                        McaFile r1 = new McaFile(file);
                        System.out.println("Diffing region " + file.getName());
                        McaFile out = diff(r1, r2, output);
                        System.out.println("Saving region " + file.getName());
                        out.write();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    private static Mode getMode(String arg) {
        switch (arg) {
            case "ADD" -> {
                return Mode.ADD;
            }
            case "DEL" -> {
                return Mode.DEL;
            }
            case "STAY" -> {
                return Mode.STAY;
            }
        }
        return Mode.ADD;
    }

    private static McaFile diff(McaFile r1, McaFile r2, String outputDir) throws IOException {
        Coordinate2D regionLocation = r1.getRegionLocation();
        File f = new File(String.format("%s/region/r.%d.%d.mca", outputDir, regionLocation.getX(), regionLocation.getZ()));
        McaFile output = McaFile.empty(f);

        Map<CoordinateDim2D, Chunk> chunks = r1.getParsedChunks(Dimension.OVERWORLD);
        Map<CoordinateDim2D, Chunk> chunks2 = r2.getParsedChunks(Dimension.OVERWORLD);

        for (int cX = 0; cX < 32; ++cX) {
            for (int cY = 0; cY < 32; ++cY) {
                CoordinateDim2D absoluteChunkPos = new CoordinateDim2D((regionLocation.getX() << 5) + cX, (regionLocation.getZ() << 5) + cY, Dimension.OVERWORLD);
                Chunk c = chunks.get(absoluteChunkPos);
                Chunk c2 = chunks2.get(absoluteChunkPos);

                if (c == null || c2 == null) {
                    continue;
                }

                Set<Byte> sectionsToRemove = new HashSet<>();
                for (byte sectionY = -5; sectionY < 20; ++sectionY) {
                    if (c.getChunkSection(sectionY) == null) {
                        if (c2.getChunkSection(sectionY) == null) {
                            continue;
                        }

                        if (mode.equals(Mode.ADD)) {
                            c.setChunkSection(sectionY, c2.getChunkSection(sectionY));
                        }
                        continue;
                    }

                    if (c2.getChunkSection(sectionY) == null) {
                        if (!mode.equals(Mode.DEL)) {
                            sectionsToRemove.add(sectionY);
                        }
                        continue;
                    }
                    ChunkSection_1_18 s = (ChunkSection_1_18) c.getChunkSection(sectionY);
                    ChunkSection_1_18 s2 = (ChunkSection_1_18) c2.getChunkSection(sectionY);

                    for (int y = 0; y < 16; ++y) {
                        for (int z = 0; z < 16; ++z) {
                            for (int x = 0; x < 16; ++x) {
                                if (compare(s.getNumericBlockStateAt(x, y, z), s2.getNumericBlockStateAt(x, y, z))) {
                                    s.setBlockAt(new Coordinate3D(x, y, z), 0); // air
                                }
                            }
                        }
                    }
                }
                for (byte y : sectionsToRemove) {
                    c.setChunkSection(y, c.createNewChunkSection(y, Palette.empty()));
                }

                c.blockEntities = diff(c.getBlockEntities(), c2.getBlockEntities());

                output.addChunk(c, false);
            }
        }
        return output;
    }

    private static Map<Coordinate3D, SpecificTag> diff(Map<Coordinate3D, SpecificTag> blockEntities1, Map<Coordinate3D, SpecificTag> blockEntities2) {
        Set<Coordinate3D> entitiesToRemove = new HashSet<>();
        for (Map.Entry<Coordinate3D, SpecificTag> entity : blockEntities1.entrySet()) {
            if (compare(entity.getValue(), blockEntities2.get(entity.getKey()))) {
                entitiesToRemove.add(entity.getKey());
            }
        }
        entitiesToRemove.forEach(blockEntities1::remove);
        return blockEntities1;
    }

    private static boolean compare(SpecificTag entity1, SpecificTag entity2) {
        boolean stay = mode.equals(Mode.STAY);
        if (entity2 == null) {
            return stay;
        }
        return (stay == !(entity1.get("id").stringValue().equals(entity2.get("id").stringValue())));
    }

    private static boolean compare(int block1, int block2) {
        boolean stay = mode.equals(Mode.STAY);

        if (isLeaves(block1, block2)) {
            final boolean isEqual = Objects.equals(leafIdToLeafTypeMap.get(block1), leafIdToLeafTypeMap.get(block2));
            return (stay != isEqual);
        }

        if (isLiquid(block1, block2)) {
            return !stay;
        }

        return (stay == (block1 != block2));
    }

    private static boolean isLiquid(int block1, int block2) {
        boolean isWater = (block1 >= 80 && block1 <= 95) && (block2 >= 80 && block2 <= 95);
        if (isWater) return true;
        return (block1 >= 96 && block1 <= 111) && (block2 >= 96 && block2 <= 111);
    }

    private static final HashMap<Integer, Integer> leafIdToLeafTypeMap = new HashMap<>();
    private static boolean isLeaves(int block1, int block2) {
        return leafIdToLeafTypeMap.containsKey(block1) && leafIdToLeafTypeMap.containsKey(block2);
    }

    private static void setupWorld() {
        Chunk_1_17.setWorldHeight(-63, 384);
        Config.setInstance(new Config());
        Config.setProtocolVersion(763);
        WorldManager man = new WorldManager();

        initLeaves();
    }

    // Needed for leaves comparing, as decay cannot be trusted between downloads
    private static void initLeaves() {
        // 349 -> 376 == acacia_leaves
        for (int i = 349; i <= 376; ++i) {
            leafIdToLeafTypeMap.put(i, 0);
        }
        // 461 -> 488 == azalea_leaves
        for (int i = 461; i <= 488; ++i) {
            leafIdToLeafTypeMap.put(i, 1);
        }
        // 293 -> 320 == birch_leaves
        for (int i = 293; i <= 320; ++i) {
            leafIdToLeafTypeMap.put(i, 2);
        }
        // 377 -> 404 == cherry_leaves
        for (int i = 377; i <= 404; ++i) {
            leafIdToLeafTypeMap.put(i, 3);
        }
        // 405 -> 432 == dark_oak_leaves
        for (int i = 405; i <= 432; ++i) {
            leafIdToLeafTypeMap.put(i, 4);
        }
        // 489 -> 516 == flowering_azalea_leaves
        for (int i = 489; i <= 516; ++i) {
            leafIdToLeafTypeMap.put(i, 5);
        }
        // 321 -> 348 == jungle_leaves
        for (int i = 321; i <= 348; ++i) {
            leafIdToLeafTypeMap.put(i, 6);
        }
        // 433 -> 460 == mangrove_leaves
        for (int i = 433; i <= 460; ++i) {
            leafIdToLeafTypeMap.put(i, 7);
        }
        // 237 -> 264 == oak_leaves
        for (int i = 237; i <= 264; ++i) {
            leafIdToLeafTypeMap.put(i, 8);
        }
        // 265 -> 292 == spruce_leaves
        for (int i = 265; i <= 292; ++i) {
            leafIdToLeafTypeMap.put(i, 9);
        }
    }
}
