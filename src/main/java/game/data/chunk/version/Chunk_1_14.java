package game.data.chunk.version;

import config.Config;
import game.data.coordinates.CoordinateDim2D;
import game.data.chunk.ChunkSection;
import game.data.chunk.palette.Palette;
import game.protocol.Protocol;
import javafx.util.Pair;
import packets.DataTypeProvider;
import packets.builder.PacketBuilder;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.StringTag;
import se.llbit.nbt.Tag;

import java.util.BitSet;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * In 1.14 the chunks are now given a heightmap in the packet. They also no longer contain light information, as
 * this was moved to a different packet. Also, a block count?
 */
public class Chunk_1_14 extends Chunk_1_13 {
    public SpecificTag heightMap;

    public Chunk_1_14(CoordinateDim2D location, int version) {
        super(location, version);
    }

    @Override
    protected void addLevelNbtTags(CompoundTag map) {
        super.addLevelNbtTags(map);

        map.add("Heightmaps", heightMap);
        map.add("Status", new StringTag("full"));
    }

    @Override
    protected void readBlockCount(DataTypeProvider provider) {
        int blockCount = provider.readShort();
    }

    @Override
    protected void parseLights(ChunkSection section, DataTypeProvider dataProvider) {
        // no lights here in 1.14+
    }

    @Override
    protected int getMinLightSection() {
        return -1;
    }

    @Override
    protected int getMinBlockSection() {
        return 0;
    }

    @Override
    protected void parseHeightMaps(DataTypeProvider dataProvider) {
        heightMap = dataProvider.readNbtTag();
    }

    @Override
    protected void parseHeightMaps(Tag tag) {
        heightMap = tag.get("Level").asCompound().get("Heightmaps").asCompound();
    }

    @Override
    protected void writeHeightMaps(PacketBuilder packet) {
        packet.writeNbt(heightMap);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Chunk_1_14 that = (Chunk_1_14) o;

        return Objects.equals(heightMap, that.heightMap);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (heightMap != null ? heightMap.hashCode() : 0);
        return result;
    }

    @Override
    public ChunkSection createNewChunkSection(byte y, Palette palette) {
        return new ChunkSection_1_14(y, palette, this);
    }

    @Override
    protected ChunkSection parseSection(int sectionY, SpecificTag section) {
        return new ChunkSection_1_14(sectionY, section, this);
    }


    @Override
    public void updateLight(DataTypeProvider provider) {
        super.updateLight(provider);

        BitSet skyLightMask = BitSet.valueOf(new long[]{(long) provider.readVarInt()});
        BitSet blockLightMask = BitSet.valueOf(new long[]{(long) provider.readVarInt()});

        BitSet emptySkyLightMask = BitSet.valueOf(new long[]{(long) provider.readVarInt()});
        BitSet emptyBlockLightMask = BitSet.valueOf(new long[]{(long) provider.readVarInt()});

        parseLightArray(skyLightMask, emptySkyLightMask, provider, ChunkSection::setSkyLight, ChunkSection::getSkyLight);
        parseLightArray(blockLightMask, emptyBlockLightMask, provider, ChunkSection::setBlockLight, ChunkSection::getBlockLight);
    }

    protected void parseLightArray(BitSet mask, BitSet emptyMask, DataTypeProvider provider, BiConsumer<ChunkSection, byte[]> c, Function<ChunkSection, byte[]> get) {
        for (int sectionY = getMinLightSection(); sectionY <= getMaxLightSection() && (!mask.isEmpty() || !emptyMask.isEmpty()); sectionY++) {
            ChunkSection s = getChunkSection(sectionY);
            if (s == null) {
                s = createNewChunkSection((byte) sectionY, Palette.empty());
                s.setBlocks(new long[256]);

                setChunkSection(sectionY, s);
            }

            // Mask tells us if a section is present or not
            if (!mask.get(sectionY - getMinLightSection())) {
                if (!emptyMask.get(sectionY - getMinLightSection())) {
                    c.accept(s, new byte[2048]);
                }
                emptyMask.set(sectionY - getMinLightSection(), false);
                continue;
            }
            mask.set(sectionY - getMinLightSection(), false);

            int skyLength = provider.readVarInt();
            byte[] data = provider.readByteArray(skyLength);

            c.accept(s, data);
        }
    }

    /**
     * Build the start of a light packet, which is always the same.
     */
    protected PacketBuilder buildLightPacket() {
        Protocol p = Config.versionReporter().getProtocol();
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt(p.clientBound("LightUpdate"));

        packet.writeVarInt(location.getX());
        packet.writeVarInt(location.getZ());

        return packet;
    }

    @Override
    public PacketBuilder toLightPacket() {
        PacketBuilder packet = buildLightPacket();

        Pair<Integer, PacketBuilder> skyLight = writeLightToPacket(ChunkSection::getSkyLight);
        Pair<Integer, PacketBuilder> blockLight = writeLightToPacket(ChunkSection::getBlockLight);

        packet.writeVarInt(skyLight.getKey());
        packet.writeVarInt(blockLight.getKey());

        // empty masks we just set to 0
        packet.writeVarInt(0);
        packet.writeVarInt(0);

        packet.writeByteArray(skyLight.getValue().toArray());
        packet.writeByteArray(blockLight.getValue().toArray());

        return packet;
    }

    /**
     * Write one of the light arrays to a packet, return the mask and the array itself.
     */
    private Pair<Integer, PacketBuilder> writeLightToPacket(Function<ChunkSection, byte[]> fn) {
        PacketBuilder packet = new PacketBuilder();
        int mask = 0;

        for (ChunkSection section : getAllSections()) {
            byte[] light = fn.apply(section);
            if (light == null || light.length == 0) { continue; }

            packet.writeVarInt(light.length);
            packet.writeByteArray(light);
            mask |= 1 << section.getY() - getMinLightSection();
        }


        return new Pair<>(mask, packet);
    }
}
