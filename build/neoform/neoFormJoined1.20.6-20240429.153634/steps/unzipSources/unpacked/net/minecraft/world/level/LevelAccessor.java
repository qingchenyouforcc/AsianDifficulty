package net.minecraft.world.level;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

public interface LevelAccessor extends CommonLevelAccessor, LevelTimeAccess {
    @Override
    default long dayTime() {
        return this.getLevelData().getDayTime();
    }

    long nextSubTickCount();

    LevelTickAccess<Block> getBlockTicks();

    private <T> ScheduledTick<T> createTick(BlockPos p_186483_, T p_186484_, int p_186485_, TickPriority p_186486_) {
        return new ScheduledTick<>(p_186484_, p_186483_, this.getLevelData().getGameTime() + (long)p_186485_, p_186486_, this.nextSubTickCount());
    }

    private <T> ScheduledTick<T> createTick(BlockPos p_186479_, T p_186480_, int p_186481_) {
        return new ScheduledTick<>(p_186480_, p_186479_, this.getLevelData().getGameTime() + (long)p_186481_, this.nextSubTickCount());
    }

    default void scheduleTick(BlockPos p_186465_, Block p_186466_, int p_186467_, TickPriority p_186468_) {
        this.getBlockTicks().schedule(this.createTick(p_186465_, p_186466_, p_186467_, p_186468_));
    }

    default void scheduleTick(BlockPos p_186461_, Block p_186462_, int p_186463_) {
        this.getBlockTicks().schedule(this.createTick(p_186461_, p_186462_, p_186463_));
    }

    LevelTickAccess<Fluid> getFluidTicks();

    default void scheduleTick(BlockPos p_186474_, Fluid p_186475_, int p_186476_, TickPriority p_186477_) {
        this.getFluidTicks().schedule(this.createTick(p_186474_, p_186475_, p_186476_, p_186477_));
    }

    default void scheduleTick(BlockPos p_186470_, Fluid p_186471_, int p_186472_) {
        this.getFluidTicks().schedule(this.createTick(p_186470_, p_186471_, p_186472_));
    }

    LevelData getLevelData();

    DifficultyInstance getCurrentDifficultyAt(BlockPos p_46800_);

    @Nullable
    MinecraftServer getServer();

    default Difficulty getDifficulty() {
        return this.getLevelData().getDifficulty();
    }

    ChunkSource getChunkSource();

    @Override
    default boolean hasChunk(int p_46794_, int p_46795_) {
        return this.getChunkSource().hasChunk(p_46794_, p_46795_);
    }

    RandomSource getRandom();

    default void blockUpdated(BlockPos p_46781_, Block p_46782_) {
    }

    default void neighborShapeChanged(Direction p_220411_, BlockState p_220412_, BlockPos p_220413_, BlockPos p_220414_, int p_220415_, int p_220416_) {
        NeighborUpdater.executeShapeUpdate(this, p_220411_, p_220412_, p_220413_, p_220414_, p_220415_, p_220416_ - 1);
    }

    default void playSound(@Nullable Player p_251195_, BlockPos p_250192_, SoundEvent p_249887_, SoundSource p_250593_) {
        this.playSound(p_251195_, p_250192_, p_249887_, p_250593_, 1.0F, 1.0F);
    }

    void playSound(@Nullable Player p_46775_, BlockPos p_46776_, SoundEvent p_46777_, SoundSource p_46778_, float p_46779_, float p_46780_);

    void addParticle(ParticleOptions p_46783_, double p_46784_, double p_46785_, double p_46786_, double p_46787_, double p_46788_, double p_46789_);

    void levelEvent(@Nullable Player p_46771_, int p_46772_, BlockPos p_46773_, int p_46774_);

    default void levelEvent(int p_46797_, BlockPos p_46798_, int p_46799_) {
        this.levelEvent(null, p_46797_, p_46798_, p_46799_);
    }

    void gameEvent(Holder<GameEvent> p_316267_, Vec3 p_220405_, GameEvent.Context p_220406_);

    default void gameEvent(@Nullable Entity p_151549_, Holder<GameEvent> p_316314_, Vec3 p_316613_) {
        this.gameEvent(p_316314_, p_316613_, new GameEvent.Context(p_151549_, null));
    }

    default void gameEvent(@Nullable Entity p_316772_, Holder<GameEvent> p_316248_, BlockPos p_316282_) {
        this.gameEvent(p_316248_, p_316282_, new GameEvent.Context(p_316772_, null));
    }

    default void gameEvent(Holder<GameEvent> p_316320_, BlockPos p_220409_, GameEvent.Context p_220410_) {
        this.gameEvent(p_316320_, Vec3.atCenterOf(p_220409_), p_220410_);
    }

    default void gameEvent(ResourceKey<GameEvent> p_316780_, BlockPos p_316509_, GameEvent.Context p_316524_) {
        this.gameEvent(this.registryAccess().registryOrThrow(Registries.GAME_EVENT).getHolderOrThrow(p_316780_), p_316509_, p_316524_);
    }
}
