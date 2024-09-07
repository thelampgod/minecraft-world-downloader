package game.data.chunk.palette;

import game.data.WorldManager;

import java.util.*;

import game.data.chunk.ChunkSection;

import java.util.stream.Stream;

import game.data.dimension.Biome;
import game.data.dimension.BiomeRegistry;
import packets.DataTypeProvider;
import packets.builder.PacketBuilder;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.SpecificTag;

/**
 * Class to hold a palette of a chunk.
 */
public class Palette {
    private int bitsPerBlock;
    private int[] palette;
    Registry registry;
    PaletteType type = PaletteType.BLOCKS;

    protected Palette() {
        // palette needs initializing
        this.palette = new int[1];
        this.registry = GlobalPaletteProvider.getGlobalPalette();
        if (this.registry == null) {
            System.out.println("No state provider available: GlobalPaletteProvider.getGlobalPalette()");
        }
    }

    private Palette(int bitsPerBlock, int[] palette) {
        this.bitsPerBlock = bitsPerBlock;
        this.palette = palette;
        this.registry = GlobalPaletteProvider.getGlobalPalette();
        synchronizeBitsPerBlock();
    }

    Palette(int[] arr) {
        this.palette = arr;
        this.bitsPerBlock = computeBitsPerBlock(Math.max(0, arr.length - 1));
        this.registry = GlobalPaletteProvider.getGlobalPalette();
    }

    public static Palette biomes(int dataVersion, ListTag palette) {
        if (palette.size() == 1) {
            return new SingleValuePalette(PaletteType.BIOMES, (SpecificTag) palette.get(0));
        }
        return new Palette(dataVersion, palette, true);
    }

    public void biomePalette() {
        Map<String, Biome> biomeMap = new HashMap<>();
        biomeMap.put("minecraft:the_void", new Biome(0));
        biomeMap.put("minecraft:plains", new Biome(1));
        biomeMap.put("minecraft:sunflower_plains", new Biome(2));
        biomeMap.put("minecraft:snowy_plains", new Biome(3));
        biomeMap.put("minecraft:ice_spikes", new Biome(4));
        biomeMap.put("minecraft:desert", new Biome(5));
        biomeMap.put("minecraft:swamp", new Biome(6));
        biomeMap.put("minecraft:mangrove_swamp", new Biome(7));
        biomeMap.put("minecraft:forest", new Biome(8));
        biomeMap.put("minecraft:flower_forest", new Biome(9));
        biomeMap.put("minecraft:birch_forest", new Biome(10));
        biomeMap.put("minecraft:dark_forest", new Biome(11));
        biomeMap.put("minecraft:old_growth_birch_forest", new Biome(12));
        biomeMap.put("minecraft:old_growth_pine_taiga", new Biome(13));
        biomeMap.put("minecraft:old_growth_spruce_taiga", new Biome(14));
        biomeMap.put("minecraft:taiga", new Biome(15));
        biomeMap.put("minecraft:snowy_taiga", new Biome(16));
        biomeMap.put("minecraft:savanna", new Biome(17));
        biomeMap.put("minecraft:savanna_plateau", new Biome(18));
        biomeMap.put("minecraft:windswept_hills", new Biome(19));
        biomeMap.put("minecraft:windswept_gravelly_hills", new Biome(20));
        biomeMap.put("minecraft:windswept_forest", new Biome(21));
        biomeMap.put("minecraft:windswept_savanna", new Biome(22));
        biomeMap.put("minecraft:jungle", new Biome(23));
        biomeMap.put("minecraft:sparse_jungle", new Biome(24));
        biomeMap.put("minecraft:bamboo_jungle", new Biome(25));
        biomeMap.put("minecraft:badlands", new Biome(26));
        biomeMap.put("minecraft:eroded_badlands", new Biome(27));
        biomeMap.put("minecraft:wooded_badlands", new Biome(28));
        biomeMap.put("minecraft:meadow", new Biome(29));
        biomeMap.put("minecraft:cherry_grove", new Biome(30));
        biomeMap.put("minecraft:grove", new Biome(31));
        biomeMap.put("minecraft:snowy_slopes", new Biome(32));
        biomeMap.put("minecraft:frozen_peaks", new Biome(33));
        biomeMap.put("minecraft:jagged_peaks", new Biome(34));
        biomeMap.put("minecraft:stony_peaks", new Biome(35));
        biomeMap.put("minecraft:river", new Biome(36));
        biomeMap.put("minecraft:frozen_river", new Biome(37));
        biomeMap.put("minecraft:beach", new Biome(38));
        biomeMap.put("minecraft:snowy_beach", new Biome(39));
        biomeMap.put("minecraft:stony_shore", new Biome(40));
        biomeMap.put("minecraft:warm_ocean", new Biome(41));
        biomeMap.put("minecraft:lukewarm_ocean", new Biome(42));
        biomeMap.put("minecraft:deep_lukewarm_ocean", new Biome(43));
        biomeMap.put("minecraft:ocean", new Biome(44));
        biomeMap.put("minecraft:deep_ocean", new Biome(45));
        biomeMap.put("minecraft:cold_ocean", new Biome(46));
        biomeMap.put("minecraft:deep_cold_ocean", new Biome(47));
        biomeMap.put("minecraft:frozen_ocean", new Biome(48));
        biomeMap.put("minecraft:deep_frozen_ocean", new Biome(49));
        biomeMap.put("minecraft:mushroom_fields", new Biome(50));
        biomeMap.put("minecraft:dripstone_caves", new Biome(51));
        biomeMap.put("minecraft:lush_caves", new Biome(52));
        biomeMap.put("minecraft:deep_dark", new Biome(53));
        biomeMap.put("minecraft:nether_wastes", new Biome(54));
        biomeMap.put("minecraft:warped_forest", new Biome(55));
        biomeMap.put("minecraft:crimson_forest", new Biome(56));
        biomeMap.put("minecraft:soul_sand_valley", new Biome(57));
        biomeMap.put("minecraft:basalt_deltas", new Biome(58));
        biomeMap.put("minecraft:the_end", new Biome(59));
        biomeMap.put("minecraft:end_highlands", new Biome(60));
        biomeMap.put("minecraft:end_midlands", new Biome(61));
        biomeMap.put("minecraft:small_end_islands", new Biome(62));
        biomeMap.put("minecraft:end_barrens", new Biome(63));
        this.registry = new BiomeRegistry(biomeMap);
        this.type = PaletteType.BIOMES;
    }

    public static Palette empty() {
        return new Palette(4, new int[1]);
    }

    public boolean hasData() {
        return true;
    }

    /**
     * Some non-vanilla servers will use more bits per block than needed, which will cause
     * issues when reading in the chunk later. To fix this, we increase the size of the
     * palette array by by adding unused block states.
     */
    private void synchronizeBitsPerBlock() {
        if (this.bitsPerBlock > 16) {
            throw new IllegalArgumentException("Bits per block may not be more than 16. Given: " + this.bitsPerBlock);
        }

        while (this.bitsPerBlock > computeBitsPerBlock(palette.length - 1)) {
            int[] newPalette = new int[palette.length + 1];
            System.arraycopy(palette, 0, newPalette, 0, palette.length);
            this.palette = newPalette;
        }
    }

    public Palette(int dataVersion, ListTag nbt) {
        this(dataVersion, nbt, false);
    }

    public Palette(int dataVersion, ListTag nbt, boolean isBiomePalette) {
        if (isBiomePalette) {
            this.biomePalette();
        } else {
            this.registry = GlobalPaletteProvider.getGlobalPalette(dataVersion);
        }

        this.palette = new int[nbt.size()];
        this.bitsPerBlock = computeBitsPerBlock(nbt.size() - 1);

        for (int i = 0; i < nbt.size(); i++) {
            int bs = this.registry.getStateId((SpecificTag) nbt.get(i));

            this.palette[i] = bs;
        }

    }

    protected void recomputeBitsPerBlock() {
        this.bitsPerBlock = computeBitsPerBlock(this.palette.length - 1);
    }

    private int computeBitsPerBlock(int maxIndex) {
        if (maxIndex < 0) {
            maxIndex = 0;
        }

        int bitsNeeded = Integer.SIZE - Integer.numberOfLeadingZeros(maxIndex);
        return Math.max(type.getMinBitsPerBlock(), bitsNeeded);
    }

    /**
     * Read the palette from the network stream.
     * @param dataTypeProvider network stream reader
     */
    public static Palette readPalette(DataTypeProvider dataTypeProvider, PaletteType type) {
        byte bitsPerBlock = dataTypeProvider.readNext();
        Palette palette = readPalette(bitsPerBlock, dataTypeProvider, type);
        palette.setType(type);
        return palette;
    }

    private void setType(PaletteType type) {
        this.type = type;
    }

    /**
     * Read the palette from the network stream.
     * @param bitsPerBlock the number of bits per block that is used, indicates the palette type
     * @param dataTypeProvider network stream reader
     */
    public static Palette readPalette(int bitsPerBlock, DataTypeProvider dataTypeProvider, PaletteType type) {
        if (bitsPerBlock == 0) {
            return new SingleValuePalette(dataTypeProvider.readVarInt());
        } else if (bitsPerBlock > type.getMaxBitsPerBlock()) {
            return new DirectPalette(bitsPerBlock);
        }
        int size = dataTypeProvider.readVarInt();
        int[] palette = dataTypeProvider.readVarIntArray(size);

        return new Palette(bitsPerBlock, palette);
    }

    /**
     * Get the block state from the palette index.
     */
    public int stateFromId(int index) {
        if (bitsPerBlock > 8) {
            return index;
        }
        if (palette.length == 0) {
            return 0;
        }
        if (index >= palette.length) {
            return 0;
        }

        return palette[index];
    }

    public boolean isEmpty() {
        return palette.length == 0 || (palette.length == 1 && palette[0] == 0);
    }

    /**
     * Create an NBT version of this palette using the block registry.
     */
    public List<SpecificTag> toNbt() {
        List<SpecificTag> tags = new ArrayList<>();

        if (registry == null) {
            throw new UnsupportedOperationException("Cannot create palette NBT without a block registry.");
        }

        for (int i : palette) {
            State state = registry.getState(i);
            if (state == null) { state = registry.getDefaultState(); }

            tags.add(state.toNbt());

        }
        return tags;
    }

    public int getBitsPerBlock() {
        return bitsPerBlock;
    }

    public void write(PacketBuilder packet) {
        packet.writeByte((byte) bitsPerBlock);
        packet.writeVarInt(palette.length);
        packet.writeVarIntArray(palette);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Palette palette1 = (Palette) o;

        if (bitsPerBlock != palette1.bitsPerBlock) return false;
        return Arrays.equals(palette, palette1.palette);
    }

    @Override
    public int hashCode() {
        int result = bitsPerBlock;
        result = 31 * result + Arrays.hashCode(palette);
        return result;
    }

    @Override
    public String toString() {
        return "Palette{" +
                "bitsPerBlock=" + bitsPerBlock +
                ", palette(" + palette.length + ")=" + Arrays.toString(palette) +
                '}';
    }

    public int getIndexFor(ChunkSection section, int blockStateId) {
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == blockStateId) {
                return i;
            }
        }

        int[] newPalette = new int[palette.length + 1];
        System.arraycopy(palette, 0, newPalette, 0, palette.length);
        newPalette[palette.length] = blockStateId;
        this.palette = newPalette;

        if (section != null) {
            resize(section, bitsPerBlock);
        }


        return palette.length - 1;
    }

    public int[] getPalette() {
        return palette;
    }

    private void resize(ChunkSection section, int oldBpp) {
        int newBitsPerBlock = computeBitsPerBlock(palette.length - 1);
        if (oldBpp != newBitsPerBlock) {
            section.resizeBlocksIfRequired(newBitsPerBlock);
            this.bitsPerBlock = newBitsPerBlock;
        }
    }


    public int size() {
        return palette.length;
    }

    public Stream<State> values() {
        return Arrays.stream(palette).mapToObj(el -> registry.getState(el));
    }
}

