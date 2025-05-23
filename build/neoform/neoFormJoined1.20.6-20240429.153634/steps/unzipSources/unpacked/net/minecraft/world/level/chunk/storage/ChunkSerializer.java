package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.slf4j.Logger;

public class ChunkSerializer {
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(
        Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState()
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_UPGRADE_DATA = "UpgradeData";
    private static final String BLOCK_TICKS_TAG = "block_ticks";
    private static final String FLUID_TICKS_TAG = "fluid_ticks";
    public static final String X_POS_TAG = "xPos";
    public static final String Z_POS_TAG = "zPos";
    public static final String HEIGHTMAPS_TAG = "Heightmaps";
    public static final String IS_LIGHT_ON_TAG = "isLightOn";
    public static final String SECTIONS_TAG = "sections";
    public static final String BLOCK_LIGHT_TAG = "BlockLight";
    public static final String SKY_LIGHT_TAG = "SkyLight";

    public static ProtoChunk read(ServerLevel p_188231_, PoiManager p_188232_, ChunkPos p_188233_, CompoundTag p_188234_) {
        ChunkPos chunkpos = new ChunkPos(p_188234_.getInt("xPos"), p_188234_.getInt("zPos"));
        if (!Objects.equals(p_188233_, chunkpos)) {
            LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", p_188233_, p_188233_, chunkpos);
        }

        UpgradeData upgradedata = p_188234_.contains("UpgradeData", 10) ? new UpgradeData(p_188234_.getCompound("UpgradeData"), p_188231_) : UpgradeData.EMPTY;
        boolean flag = p_188234_.getBoolean("isLightOn");
        ListTag listtag = p_188234_.getList("sections", 10);
        int i = p_188231_.getSectionsCount();
        LevelChunkSection[] alevelchunksection = new LevelChunkSection[i];
        boolean flag1 = p_188231_.dimensionType().hasSkyLight();
        ChunkSource chunksource = p_188231_.getChunkSource();
        LevelLightEngine levellightengine = chunksource.getLightEngine();
        Registry<Biome> registry = p_188231_.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = makeBiomeCodec(registry);
        boolean flag2 = false;

        for (int j = 0; j < listtag.size(); j++) {
            CompoundTag compoundtag = listtag.getCompound(j);
            int k = compoundtag.getByte("Y");
            int l = p_188231_.getSectionIndexFromSectionY(k);
            if (l >= 0 && l < alevelchunksection.length) {
                PalettedContainer<BlockState> palettedcontainer;
                if (compoundtag.contains("block_states", 10)) {
                    palettedcontainer = BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, compoundtag.getCompound("block_states"))
                        .promotePartial(p_188283_ -> logErrors(p_188233_, k, p_188283_))
                        .getOrThrow(ChunkSerializer.ChunkReadException::new);
                } else {
                    palettedcontainer = new PalettedContainer<>(
                        Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES
                    );
                }

                PalettedContainerRO<Holder<Biome>> palettedcontainerro;
                if (compoundtag.contains("biomes", 10)) {
                    palettedcontainerro = codec.parse(NbtOps.INSTANCE, compoundtag.getCompound("biomes"))
                        .promotePartial(p_188274_ -> logErrors(p_188233_, k, p_188274_))
                        .getOrThrow(ChunkSerializer.ChunkReadException::new);
                } else {
                    palettedcontainerro = new PalettedContainer<>(
                        registry.asHolderIdMap(), registry.getHolderOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES
                    );
                }

                LevelChunkSection levelchunksection = new LevelChunkSection(palettedcontainer, palettedcontainerro);
                alevelchunksection[l] = levelchunksection;
                SectionPos sectionpos = SectionPos.of(p_188233_, k);
                p_188232_.checkConsistencyWithBlocks(sectionpos, levelchunksection);
            }

            boolean flag3 = compoundtag.contains("BlockLight", 7);
            boolean flag4 = flag1 && compoundtag.contains("SkyLight", 7);
            if (flag3 || flag4) {
                if (!flag2) {
                    levellightengine.retainData(p_188233_, true);
                    flag2 = true;
                }

                if (flag3) {
                    levellightengine.queueSectionData(LightLayer.BLOCK, SectionPos.of(p_188233_, k), new DataLayer(compoundtag.getByteArray("BlockLight")));
                }

                if (flag4) {
                    levellightengine.queueSectionData(LightLayer.SKY, SectionPos.of(p_188233_, k), new DataLayer(compoundtag.getByteArray("SkyLight")));
                }
            }
        }

        long k1 = p_188234_.getLong("InhabitedTime");
        ChunkType chunktype = getChunkTypeFromTag(p_188234_);
        BlendingData blendingdata;
        if (p_188234_.contains("blending_data", 10)) {
            blendingdata = BlendingData.CODEC
                .parse(new Dynamic<>(NbtOps.INSTANCE, p_188234_.getCompound("blending_data")))
                .resultOrPartial(LOGGER::error)
                .orElse(null);
        } else {
            blendingdata = null;
        }

        ChunkAccess chunkaccess;
        if (chunktype == ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> levelchunkticks = LevelChunkTicks.load(
                p_188234_.getList("block_ticks", 10), p_258988_ -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(p_258988_)), p_188233_
            );
            LevelChunkTicks<Fluid> levelchunkticks1 = LevelChunkTicks.load(
                p_188234_.getList("fluid_ticks", 10), p_258990_ -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(p_258990_)), p_188233_
            );
            chunkaccess = new LevelChunk(
                p_188231_.getLevel(),
                p_188233_,
                upgradedata,
                levelchunkticks,
                levelchunkticks1,
                k1,
                alevelchunksection,
                postLoadChunk(p_188231_, p_188234_),
                blendingdata
            );
            if (p_188234_.contains(net.neoforged.neoforge.common.world.LevelChunkAuxiliaryLightManager.LIGHT_NBT_KEY, net.minecraft.nbt.Tag.TAG_LIST))
                Objects.requireNonNull(((LevelChunk)chunkaccess).getAuxLightManager(p_188233_)).deserializeNBT(p_188231_.registryAccess(), p_188234_.getList(net.neoforged.neoforge.common.world.LevelChunkAuxiliaryLightManager.LIGHT_NBT_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND));
        } else {
            ProtoChunkTicks<Block> protochunkticks = ProtoChunkTicks.load(
                p_188234_.getList("block_ticks", 10), p_258992_ -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(p_258992_)), p_188233_
            );
            ProtoChunkTicks<Fluid> protochunkticks1 = ProtoChunkTicks.load(
                p_188234_.getList("fluid_ticks", 10), p_258991_ -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(p_258991_)), p_188233_
            );
            ProtoChunk protochunk = new ProtoChunk(
                p_188233_, upgradedata, alevelchunksection, protochunkticks, protochunkticks1, p_188231_, registry, blendingdata
            );
            chunkaccess = protochunk;
            protochunk.setInhabitedTime(k1);
            if (p_188234_.contains("below_zero_retrogen", 10)) {
                BelowZeroRetrogen.CODEC
                    .parse(new Dynamic<>(NbtOps.INSTANCE, p_188234_.getCompound("below_zero_retrogen")))
                    .resultOrPartial(LOGGER::error)
                    .ifPresent(protochunk::setBelowZeroRetrogen);
            }

            ChunkStatus chunkstatus = ChunkStatus.byName(p_188234_.getString("Status"));
            protochunk.setStatus(chunkstatus);
            if (chunkstatus.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                protochunk.setLightEngine(levellightengine);
            }
        }

        if (p_188234_.contains(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND))
            chunkaccess.readAttachmentsFromNBT(p_188231_.registryAccess(), p_188234_.getCompound(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY));
        chunkaccess.setLightCorrect(flag);
        CompoundTag compoundtag2 = p_188234_.getCompound("Heightmaps");
        EnumSet<Heightmap.Types> enumset = EnumSet.noneOf(Heightmap.Types.class);

        for (Heightmap.Types heightmap$types : chunkaccess.getStatus().heightmapsAfter()) {
            String s = heightmap$types.getSerializationKey();
            if (compoundtag2.contains(s, 12)) {
                chunkaccess.setHeightmap(heightmap$types, compoundtag2.getLongArray(s));
            } else {
                enumset.add(heightmap$types);
            }
        }

        Heightmap.primeHeightmaps(chunkaccess, enumset);
        CompoundTag compoundtag3 = p_188234_.getCompound("structures");
        chunkaccess.setAllStarts(unpackStructureStart(StructurePieceSerializationContext.fromLevel(p_188231_), compoundtag3, p_188231_.getSeed()));
        chunkaccess.setAllReferences(unpackStructureReferences(p_188231_.registryAccess(), p_188233_, compoundtag3));
        if (p_188234_.getBoolean("shouldSave")) {
            chunkaccess.setUnsaved(true);
        }

        ListTag listtag2 = p_188234_.getList("PostProcessing", 9);

        for (int l1 = 0; l1 < listtag2.size(); l1++) {
            ListTag listtag1 = listtag2.getList(l1);

            for (int i1 = 0; i1 < listtag1.size(); i1++) {
                chunkaccess.addPackedPostProcess(listtag1.getShort(i1), l1);
            }
        }

        if (chunktype == ChunkType.LEVELCHUNK) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkDataEvent.Load(chunkaccess, p_188234_, chunktype));
            return new ImposterProtoChunk((LevelChunk)chunkaccess, false);
        } else {
            ProtoChunk protochunk1 = (ProtoChunk)chunkaccess;
            ListTag listtag3 = p_188234_.getList("entities", 10);

            for (int i2 = 0; i2 < listtag3.size(); i2++) {
                protochunk1.addEntity(listtag3.getCompound(i2));
            }

            ListTag listtag4 = p_188234_.getList("block_entities", 10);

            for (int j1 = 0; j1 < listtag4.size(); j1++) {
                CompoundTag compoundtag1 = listtag4.getCompound(j1);
                chunkaccess.setBlockEntityNbt(compoundtag1);
            }

            CompoundTag compoundtag4 = p_188234_.getCompound("CarvingMasks");

            for (String s1 : compoundtag4.getAllKeys()) {
                GenerationStep.Carving generationstep$carving = GenerationStep.Carving.valueOf(s1);
                protochunk1.setCarvingMask(generationstep$carving, new CarvingMask(compoundtag4.getLongArray(s1), chunkaccess.getMinBuildHeight()));
            }

            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkDataEvent.Load(chunkaccess, p_188234_, chunktype));
            return protochunk1;
        }
    }

    private static void logErrors(ChunkPos p_188240_, int p_188241_, String p_188242_) {
        LOGGER.error("Recoverable errors when loading section [" + p_188240_.x + ", " + p_188241_ + ", " + p_188240_.z + "]: " + p_188242_);
    }

    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> p_188261_) {
        return PalettedContainer.codecRO(
            p_188261_.asHolderIdMap(), p_188261_.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, p_188261_.getHolderOrThrow(Biomes.PLAINS)
        );
    }

    public static CompoundTag write(ServerLevel p_63455_, ChunkAccess p_63456_) {
        ChunkPos chunkpos = p_63456_.getPos();
        CompoundTag compoundtag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        compoundtag.putInt("xPos", chunkpos.x);
        compoundtag.putInt("yPos", p_63456_.getMinSection());
        compoundtag.putInt("zPos", chunkpos.z);
        compoundtag.putLong("LastUpdate", p_63455_.getGameTime());
        compoundtag.putLong("InhabitedTime", p_63456_.getInhabitedTime());
        compoundtag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(p_63456_.getStatus()).toString());
        BlendingData blendingdata = p_63456_.getBlendingData();
        if (blendingdata != null) {
            BlendingData.CODEC
                .encodeStart(NbtOps.INSTANCE, blendingdata)
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_196909_ -> compoundtag.put("blending_data", p_196909_));
        }

        BelowZeroRetrogen belowzeroretrogen = p_63456_.getBelowZeroRetrogen();
        if (belowzeroretrogen != null) {
            BelowZeroRetrogen.CODEC
                .encodeStart(NbtOps.INSTANCE, belowzeroretrogen)
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_188279_ -> compoundtag.put("below_zero_retrogen", p_188279_));
        }

        UpgradeData upgradedata = p_63456_.getUpgradeData();
        if (!upgradedata.isEmpty()) {
            compoundtag.put("UpgradeData", upgradedata.write());
        }

        LevelChunkSection[] alevelchunksection = p_63456_.getSections();
        ListTag listtag = new ListTag();
        LevelLightEngine levellightengine = p_63455_.getChunkSource().getLightEngine();
        Registry<Biome> registry = p_63455_.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = makeBiomeCodec(registry);
        boolean flag = p_63456_.isLightCorrect();

        for (int i = levellightengine.getMinLightSection(); i < levellightengine.getMaxLightSection(); i++) {
            int j = p_63456_.getSectionIndexFromSectionY(i);
            boolean flag1 = j >= 0 && j < alevelchunksection.length;
            DataLayer datalayer = levellightengine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkpos, i));
            DataLayer datalayer1 = levellightengine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkpos, i));
            if (flag1 || datalayer != null || datalayer1 != null) {
                CompoundTag compoundtag1 = new CompoundTag();
                if (flag1) {
                    LevelChunkSection levelchunksection = alevelchunksection[j];
                    compoundtag1.put("block_states", BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, levelchunksection.getStates()).getOrThrow());
                    compoundtag1.put("biomes", codec.encodeStart(NbtOps.INSTANCE, levelchunksection.getBiomes()).getOrThrow());
                }

                if (datalayer != null && !datalayer.isEmpty()) {
                    compoundtag1.putByteArray("BlockLight", datalayer.getData());
                }

                if (datalayer1 != null && !datalayer1.isEmpty()) {
                    compoundtag1.putByteArray("SkyLight", datalayer1.getData());
                }

                if (!compoundtag1.isEmpty()) {
                    compoundtag1.putByte("Y", (byte)i);
                    listtag.add(compoundtag1);
                }
            }
        }

        compoundtag.put("sections", listtag);
        if (flag) {
            compoundtag.putBoolean("isLightOn", true);
        }

        ListTag listtag1 = new ListTag();

        for (BlockPos blockpos : p_63456_.getBlockEntitiesPos()) {
            CompoundTag compoundtag3 = p_63456_.getBlockEntityNbtForSaving(blockpos, p_63455_.registryAccess());
            if (compoundtag3 != null) {
                listtag1.add(compoundtag3);
            }
        }

        compoundtag.put("block_entities", listtag1);
        if (p_63456_.getStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protochunk = (ProtoChunk)p_63456_;
            ListTag listtag2 = new ListTag();
            listtag2.addAll(protochunk.getEntities());
            compoundtag.put("entities", listtag2);
            CompoundTag compoundtag4 = new CompoundTag();

            for (GenerationStep.Carving generationstep$carving : GenerationStep.Carving.values()) {
                CarvingMask carvingmask = protochunk.getCarvingMask(generationstep$carving);
                if (carvingmask != null) {
                    compoundtag4.putLongArray(generationstep$carving.toString(), carvingmask.toArray());
                }
            }

            compoundtag.put("CarvingMasks", compoundtag4);
        }
        else if (p_63456_ instanceof LevelChunk levelChunk){

             Tag lightTag = levelChunk.getAuxLightManager(chunkpos).serializeNBT(p_63455_.registryAccess());
             if (lightTag != null) compoundtag.put(net.neoforged.neoforge.common.world.LevelChunkAuxiliaryLightManager.LIGHT_NBT_KEY, lightTag);
        }

        saveTicks(p_63455_, compoundtag, p_63456_.getTicksForSerialization());
        compoundtag.put("PostProcessing", packOffsets(p_63456_.getPostProcessing()));
        CompoundTag compoundtag2 = new CompoundTag();

        for (Entry<Heightmap.Types, Heightmap> entry : p_63456_.getHeightmaps()) {
            if (p_63456_.getStatus().heightmapsAfter().contains(entry.getKey())) {
                compoundtag2.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        try {
            final CompoundTag capTag = p_63456_.writeAttachmentsToNBT(p_63455_.registryAccess());
            if (capTag != null) compoundtag.put(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY, capTag);
        } catch (Exception exception) {
            LOGGER.error("Failed to write chunk attachments. An attachment has likely thrown an exception trying to write state. It will not persist. Report this to the mod author", exception);
        }
        compoundtag.put("Heightmaps", compoundtag2);
        compoundtag.put(
            "structures",
            packStructureData(StructurePieceSerializationContext.fromLevel(p_63455_), chunkpos, p_63456_.getAllStarts(), p_63456_.getAllReferences())
        );
        return compoundtag;
    }

    private static void saveTicks(ServerLevel p_188236_, CompoundTag p_188237_, ChunkAccess.TicksToSave p_188238_) {
        long i = p_188236_.getLevelData().getGameTime();
        p_188237_.put("block_ticks", p_188238_.blocks().save(i, p_258987_ -> BuiltInRegistries.BLOCK.getKey(p_258987_).toString()));
        p_188237_.put("fluid_ticks", p_188238_.fluids().save(i, p_258989_ -> BuiltInRegistries.FLUID.getKey(p_258989_).toString()));
    }

    public static ChunkType getChunkTypeFromTag(@Nullable CompoundTag p_63486_) {
        return p_63486_ != null ? ChunkStatus.byName(p_63486_.getString("Status")).getChunkType() : ChunkType.PROTOCHUNK;
    }

    @Nullable
    private static LevelChunk.PostLoadProcessor postLoadChunk(ServerLevel p_196891_, CompoundTag p_196892_) {
        ListTag listtag = getListOfCompoundsOrNull(p_196892_, "entities");
        ListTag listtag1 = getListOfCompoundsOrNull(p_196892_, "block_entities");
        return listtag == null && listtag1 == null ? null : p_196904_ -> {
            if (listtag != null) {
                p_196891_.addLegacyChunkEntities(EntityType.loadEntitiesRecursive(listtag, p_196891_));
            }

            if (listtag1 != null) {
                for (int i = 0; i < listtag1.size(); i++) {
                    CompoundTag compoundtag = listtag1.getCompound(i);
                    boolean flag = compoundtag.getBoolean("keepPacked");
                    if (flag) {
                        p_196904_.setBlockEntityNbt(compoundtag);
                    } else {
                        BlockPos blockpos = BlockEntity.getPosFromTag(compoundtag);
                        BlockEntity blockentity = BlockEntity.loadStatic(blockpos, p_196904_.getBlockState(blockpos), compoundtag, p_196891_.registryAccess());
                        if (blockentity != null) {
                            p_196904_.setBlockEntity(blockentity);
                        }
                    }
                }
            }
        };
    }

    @Nullable
    private static ListTag getListOfCompoundsOrNull(CompoundTag p_196898_, String p_196899_) {
        ListTag listtag = p_196898_.getList(p_196899_, 10);
        return listtag.isEmpty() ? null : listtag;
    }

    private static CompoundTag packStructureData(
        StructurePieceSerializationContext p_188250_, ChunkPos p_188251_, Map<Structure, StructureStart> p_188252_, Map<Structure, LongSet> p_188253_
    ) {
        CompoundTag compoundtag = new CompoundTag();
        CompoundTag compoundtag1 = new CompoundTag();
        Registry<Structure> registry = p_188250_.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (Entry<Structure, StructureStart> entry : p_188252_.entrySet()) {
            ResourceLocation resourcelocation = registry.getKey(entry.getKey());
            compoundtag1.put(resourcelocation.toString(), entry.getValue().createTag(p_188250_, p_188251_));
        }

        compoundtag.put("starts", compoundtag1);
        CompoundTag compoundtag2 = new CompoundTag();

        for (Entry<Structure, LongSet> entry1 : p_188253_.entrySet()) {
            if (!entry1.getValue().isEmpty()) {
                ResourceLocation resourcelocation1 = registry.getKey(entry1.getKey());
                compoundtag2.put(resourcelocation1.toString(), new LongArrayTag(entry1.getValue()));
            }
        }

        compoundtag.put("References", compoundtag2);
        return compoundtag;
    }

    private static Map<Structure, StructureStart> unpackStructureStart(StructurePieceSerializationContext p_188255_, CompoundTag p_188256_, long p_188257_) {
        Map<Structure, StructureStart> map = Maps.newHashMap();
        Registry<Structure> registry = p_188255_.registryAccess().registryOrThrow(Registries.STRUCTURE);
        CompoundTag compoundtag = p_188256_.getCompound("starts");

        for (String s : compoundtag.getAllKeys()) {
            ResourceLocation resourcelocation = ResourceLocation.tryParse(s);
            Structure structure = registry.get(resourcelocation);
            if (structure == null) {
                LOGGER.error("Unknown structure start: {}", resourcelocation);
            } else {
                StructureStart structurestart = StructureStart.loadStaticStart(p_188255_, compoundtag.getCompound(s), p_188257_);
                if (structurestart != null) {
                    map.put(structure, structurestart);
                }
            }
        }

        return map;
    }

    private static Map<Structure, LongSet> unpackStructureReferences(RegistryAccess p_208155_, ChunkPos p_208156_, CompoundTag p_208157_) {
        Map<Structure, LongSet> map = Maps.newHashMap();
        Registry<Structure> registry = p_208155_.registryOrThrow(Registries.STRUCTURE);
        CompoundTag compoundtag = p_208157_.getCompound("References");

        for (String s : compoundtag.getAllKeys()) {
            ResourceLocation resourcelocation = ResourceLocation.tryParse(s);
            Structure structure = registry.get(resourcelocation);
            if (structure == null) {
                LOGGER.warn("Found reference to unknown structure '{}' in chunk {}, discarding", resourcelocation, p_208156_);
            } else {
                long[] along = compoundtag.getLongArray(s);
                if (along.length != 0) {
                    map.put(structure, new LongOpenHashSet(Arrays.stream(along).filter(p_208153_ -> {
                        ChunkPos chunkpos = new ChunkPos(p_208153_);
                        if (chunkpos.getChessboardDistance(p_208156_) > 8) {
                            LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", resourcelocation, chunkpos, p_208156_);
                            return false;
                        } else {
                            return true;
                        }
                    }).toArray()));
                }
            }
        }

        return map;
    }

    public static ListTag packOffsets(ShortList[] p_63491_) {
        ListTag listtag = new ListTag();

        for (ShortList shortlist : p_63491_) {
            ListTag listtag1 = new ListTag();
            if (shortlist != null) {
                for (Short oshort : shortlist) {
                    listtag1.add(ShortTag.valueOf(oshort));
                }
            }

            listtag.add(listtag1);
        }

        return listtag;
    }

    public static class ChunkReadException extends NbtException {
        public ChunkReadException(String p_331759_) {
            super(p_331759_);
        }
    }
}
