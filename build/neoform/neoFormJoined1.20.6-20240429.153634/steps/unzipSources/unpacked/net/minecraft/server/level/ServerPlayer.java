package net.minecraft.server.level;

import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ComplexItem;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ServerItemCooldowns;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;

public class ServerPlayer extends Player {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_XZ = 32;
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_Y = 10;
    private static final int FLY_STAT_RECORDING_SPEED = 25;
    public static final double INTERACTION_DISTANCE_VERIFICATION_BUFFER = 1.0;
    private static final AttributeModifier CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER = new AttributeModifier(
        UUID.fromString("736565d2-e1a7-403d-a3f8-1aeb3e302542"), "Creative block interaction range modifier", 0.5, AttributeModifier.Operation.ADD_VALUE
    );
    private static final AttributeModifier CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER = new AttributeModifier(
        UUID.fromString("98491ef6-97b1-4584-ae82-71a8cc85cf73"), "Creative entity interaction range modifier", 2.0, AttributeModifier.Operation.ADD_VALUE
    );
    public ServerGamePacketListenerImpl connection;
    public final MinecraftServer server;
    public final ServerPlayerGameMode gameMode;
    private final PlayerAdvancements advancements;
    private final ServerStatsCounter stats;
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE;
    private int lastRecordedAirLevel = Integer.MIN_VALUE;
    private int lastRecordedArmor = Integer.MIN_VALUE;
    private int lastRecordedLevel = Integer.MIN_VALUE;
    private int lastRecordedExperience = Integer.MIN_VALUE;
    private float lastSentHealth = -1.0E8F;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    private int lastSentExp = -99999999;
    private int spawnInvulnerableTime = 60;
    private ChatVisiblity chatVisibility = ChatVisiblity.FULL;
    private boolean canChatColor = true;
    private long lastActionTime = Util.getMillis();
    @Nullable
    private Entity camera;
    private boolean isChangingDimension;
    private boolean seenCredits;
    private final ServerRecipeBook recipeBook = new ServerRecipeBook();
    @Nullable
    private Vec3 levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    private int requestedViewDistance = 2;
    private String language = "en_us";
    @Nullable
    private Vec3 startingToFallPosition;
    @Nullable
    private Vec3 enteredNetherPosition;
    @Nullable
    private Vec3 enteredLavaOnVehiclePosition;
    private SectionPos lastSectionPos = SectionPos.of(0, 0, 0);
    private ChunkTrackingView chunkTrackingView = ChunkTrackingView.EMPTY;
    private ResourceKey<Level> respawnDimension = Level.OVERWORLD;
    @Nullable
    private BlockPos respawnPosition;
    private boolean respawnForced;
    private float respawnAngle;
    private final TextFilter textFilter;
    private boolean textFilteringEnabled;
    private boolean allowsListing;
    private boolean spawnExtraParticlesOnFall;
    private WardenSpawnTracker wardenSpawnTracker = new WardenSpawnTracker(0, 0, 0);
    @Nullable
    private BlockPos raidOmenPosition;
    private final ContainerSynchronizer containerSynchronizer = new ContainerSynchronizer() {
        @Override
        public void sendInitialData(AbstractContainerMenu p_143448_, NonNullList<ItemStack> p_143449_, ItemStack p_143450_, int[] p_143451_) {
            ServerPlayer.this.connection
                .send(new ClientboundContainerSetContentPacket(p_143448_.containerId, p_143448_.incrementStateId(), p_143449_, p_143450_));

            for (int i = 0; i < p_143451_.length; i++) {
                this.broadcastDataValue(p_143448_, i, p_143451_[i]);
            }
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu p_143441_, int p_143442_, ItemStack p_143443_) {
            ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(p_143441_.containerId, p_143441_.incrementStateId(), p_143442_, p_143443_));
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu p_143445_, ItemStack p_143446_) {
            ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(-1, p_143445_.incrementStateId(), -1, p_143446_));
        }

        @Override
        public void sendDataChange(AbstractContainerMenu p_143437_, int p_143438_, int p_143439_) {
            this.broadcastDataValue(p_143437_, p_143438_, p_143439_);
        }

        private void broadcastDataValue(AbstractContainerMenu p_143455_, int p_143456_, int p_143457_) {
            if (ServerPlayer.this.connection.hasChannel(net.neoforged.neoforge.network.payload.AdvancedContainerSetDataPayload.TYPE)) {
                ServerPlayer.this.connection.send(new net.neoforged.neoforge.network.payload.AdvancedContainerSetDataPayload((byte) p_143455_.containerId, (short) p_143456_, p_143457_));
                return;
            }
            ServerPlayer.this.connection.send(new ClientboundContainerSetDataPacket(p_143455_.containerId, p_143456_, p_143457_));
        }
    };
    private final ContainerListener containerListener = new ContainerListener() {
        @Override
        public void slotChanged(AbstractContainerMenu p_143466_, int p_143467_, ItemStack p_143468_) {
            Slot slot = p_143466_.getSlot(p_143467_);
            if (!(slot instanceof ResultSlot)) {
                if (slot.container == ServerPlayer.this.getInventory()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), p_143468_);
                }
            }
        }

        @Override
        public void dataChanged(AbstractContainerMenu p_143462_, int p_143463_, int p_143464_) {
        }
    };
    @Nullable
    private RemoteChatSession chatSession;
    @Nullable
    public final Object object;
    public int containerCounter;
    public boolean wonGame;

    public ServerPlayer(MinecraftServer p_254143_, ServerLevel p_254435_, GameProfile p_253651_, ClientInformation p_301997_) {
        super(p_254435_, p_254435_.getSharedSpawnPos(), p_254435_.getSharedSpawnAngle(), p_253651_);
        this.textFilter = p_254143_.createTextFilterForPlayer(this);
        this.gameMode = p_254143_.createGameModeForPlayer(this);
        this.server = p_254143_;
        this.stats = p_254143_.getPlayerList().getPlayerStats(this);
        this.advancements = p_254143_.getPlayerList().getPlayerAdvancements(this);
        this.fudgeSpawnLocation(p_254435_);
        this.updateOptions(p_301997_);
        this.object = null;
    }

    private void fudgeSpawnLocation(ServerLevel p_9202_) {
        BlockPos blockpos = p_9202_.getSharedSpawnPos();
        if (p_9202_.dimensionType().hasSkyLight() && p_9202_.getServer().getWorldData().getGameType() != GameType.ADVENTURE) {
            int i = Math.max(0, this.server.getSpawnRadius(p_9202_));
            int j = Mth.floor(p_9202_.getWorldBorder().getDistanceToBorder((double)blockpos.getX(), (double)blockpos.getZ()));
            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long)(i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int)l;
            int j1 = this.getCoprime(i1);
            int k1 = RandomSource.create().nextInt(i1);

            for (int l1 = 0; l1 < i1; l1++) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                BlockPos blockpos1 = PlayerRespawnLogic.getOverworldRespawnPos(p_9202_, blockpos.getX() + j2 - i, blockpos.getZ() + k2 - i);
                if (blockpos1 != null) {
                    this.moveTo(blockpos1, 0.0F, 0.0F);
                    if (p_9202_.noCollision(this)) {
                        break;
                    }
                }
            }
        } else {
            this.moveTo(blockpos, 0.0F, 0.0F);

            while (!p_9202_.noCollision(this) && this.getY() < (double)(p_9202_.getMaxBuildHeight() - 1)) {
                this.setPos(this.getX(), this.getY() + 1.0, this.getZ());
            }
        }
    }

    private int getCoprime(int p_9238_) {
        return p_9238_ <= 16 ? p_9238_ - 1 : 17;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_9131_) {
        super.readAdditionalSaveData(p_9131_);
        if (p_9131_.contains("warden_spawn_tracker", 10)) {
            WardenSpawnTracker.CODEC
                .parse(new Dynamic<>(NbtOps.INSTANCE, p_9131_.get("warden_spawn_tracker")))
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_248205_ -> this.wardenSpawnTracker = p_248205_);
        }

        if (p_9131_.contains("enteredNetherPosition", 10)) {
            CompoundTag compoundtag = p_9131_.getCompound("enteredNetherPosition");
            this.enteredNetherPosition = new Vec3(compoundtag.getDouble("x"), compoundtag.getDouble("y"), compoundtag.getDouble("z"));
        }

        this.seenCredits = p_9131_.getBoolean("seenCredits");
        if (p_9131_.contains("recipeBook", 10)) {
            this.recipeBook.fromNbt(p_9131_.getCompound("recipeBook"), this.server.getRecipeManager());
        }

        if (this.isSleeping()) {
            this.stopSleeping();
        }

        if (p_9131_.contains("SpawnX", 99) && p_9131_.contains("SpawnY", 99) && p_9131_.contains("SpawnZ", 99)) {
            this.respawnPosition = new BlockPos(p_9131_.getInt("SpawnX"), p_9131_.getInt("SpawnY"), p_9131_.getInt("SpawnZ"));
            this.respawnForced = p_9131_.getBoolean("SpawnForced");
            this.respawnAngle = p_9131_.getFloat("SpawnAngle");
            if (p_9131_.contains("SpawnDimension")) {
                this.respawnDimension = Level.RESOURCE_KEY_CODEC
                    .parse(NbtOps.INSTANCE, p_9131_.get("SpawnDimension"))
                    .resultOrPartial(LOGGER::error)
                    .orElse(Level.OVERWORLD);
            }
        }

        this.spawnExtraParticlesOnFall = p_9131_.getBoolean("spawn_extra_particles_on_fall");
        Tag tag = p_9131_.get("raid_omen_position");
        if (tag != null) {
            BlockPos.CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial(LOGGER::error).ifPresent(p_337547_ -> this.raidOmenPosition = p_337547_);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_9197_) {
        super.addAdditionalSaveData(p_9197_);
        WardenSpawnTracker.CODEC
            .encodeStart(NbtOps.INSTANCE, this.wardenSpawnTracker)
            .resultOrPartial(LOGGER::error)
            .ifPresent(p_9134_ -> p_9197_.put("warden_spawn_tracker", p_9134_));
        this.storeGameTypes(p_9197_);
        p_9197_.putBoolean("seenCredits", this.seenCredits);
        if (this.enteredNetherPosition != null) {
            CompoundTag compoundtag = new CompoundTag();
            compoundtag.putDouble("x", this.enteredNetherPosition.x);
            compoundtag.putDouble("y", this.enteredNetherPosition.y);
            compoundtag.putDouble("z", this.enteredNetherPosition.z);
            p_9197_.put("enteredNetherPosition", compoundtag);
        }

        Entity entity1 = this.getRootVehicle();
        Entity entity = this.getVehicle();
        if (entity != null && entity1 != this && entity1.hasExactlyOnePlayerPassenger()) {
            CompoundTag compoundtag1 = new CompoundTag();
            CompoundTag compoundtag2 = new CompoundTag();
            entity1.save(compoundtag2);
            compoundtag1.putUUID("Attach", entity.getUUID());
            compoundtag1.put("Entity", compoundtag2);
            p_9197_.put("RootVehicle", compoundtag1);
        }

        p_9197_.put("recipeBook", this.recipeBook.toNbt());
        p_9197_.putString("Dimension", this.level().dimension().location().toString());
        if (this.respawnPosition != null) {
            p_9197_.putInt("SpawnX", this.respawnPosition.getX());
            p_9197_.putInt("SpawnY", this.respawnPosition.getY());
            p_9197_.putInt("SpawnZ", this.respawnPosition.getZ());
            p_9197_.putBoolean("SpawnForced", this.respawnForced);
            p_9197_.putFloat("SpawnAngle", this.respawnAngle);
            ResourceLocation.CODEC
                .encodeStart(NbtOps.INSTANCE, this.respawnDimension.location())
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_248207_ -> p_9197_.put("SpawnDimension", p_248207_));
        }

        p_9197_.putBoolean("spawn_extra_particles_on_fall", this.spawnExtraParticlesOnFall);
        if (this.raidOmenPosition != null) {
            BlockPos.CODEC
                .encodeStart(NbtOps.INSTANCE, this.raidOmenPosition)
                .resultOrPartial(LOGGER::error)
                .ifPresent(p_337549_ -> p_9197_.put("raid_omen_position", p_337549_));
        }
    }

    public void setExperiencePoints(int p_8986_) {
        float f = (float)this.getXpNeededForNextLevel();
        float f1 = (f - 1.0F) / f;
        this.experienceProgress = Mth.clamp((float)p_8986_ / f, 0.0F, f1);
        this.lastSentExp = -1;
    }

    public void setExperienceLevels(int p_9175_) {
        this.experienceLevel = p_9175_;
        this.lastSentExp = -1;
    }

    @Override
    public void giveExperienceLevels(int p_9200_) {
        super.giveExperienceLevels(p_9200_);
        this.lastSentExp = -1;
    }

    @Override
    public void onEnchantmentPerformed(ItemStack p_9079_, int p_9080_) {
        super.onEnchantmentPerformed(p_9079_, p_9080_);
        this.lastSentExp = -1;
    }

    public void initMenu(AbstractContainerMenu p_143400_) {
        p_143400_.addSlotListener(this.containerListener);
        p_143400_.setSynchronizer(this.containerSynchronizer);
    }

    public void initInventoryMenu() {
        this.initMenu(this.inventoryMenu);
    }

    @Override
    public void onEnterCombat() {
        super.onEnterCombat();
        this.connection.send(ClientboundPlayerCombatEnterPacket.INSTANCE);
    }

    @Override
    public void onLeaveCombat() {
        super.onLeaveCombat();
        this.connection.send(new ClientboundPlayerCombatEndPacket(this.getCombatTracker()));
    }

    @Override
    protected void onInsideBlock(BlockState p_9103_) {
        CriteriaTriggers.ENTER_BLOCK.trigger(this, p_9103_);
    }

    @Override
    protected ItemCooldowns createItemCooldowns() {
        return new ServerItemCooldowns(this);
    }

    @Override
    public void tick() {
        this.gameMode.tick();
        this.wardenSpawnTracker.tick();
        this.spawnInvulnerableTime--;
        if (this.invulnerableTime > 0) {
            this.invulnerableTime--;
        }

        this.containerMenu.broadcastChanges();
        if (!this.level().isClientSide && !this.containerMenu.stillValid(this)) {
            this.closeContainer();
            this.containerMenu = this.inventoryMenu;
        }

        Entity entity = this.getCamera();
        if (entity != this) {
            if (entity.isAlive()) {
                this.absMoveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                this.serverLevel().getChunkSource().move(this);
                if (this.wantsToStopRiding()) {
                    this.setCamera(this);
                }
            } else {
                this.setCamera(this);
            }
        }

        CriteriaTriggers.TICK.trigger(this);
        if (this.levitationStartPos != null) {
            CriteriaTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
        }

        this.trackStartFallingPosition();
        this.trackEnteredOrExitedLavaOnVehicle();
        this.updatePlayerAttributes();
        this.advancements.flushDirty(this);
    }

    private void updatePlayerAttributes() {
        AttributeInstance attributeinstance = this.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (attributeinstance != null) {
            if (this.isCreative()) {
                attributeinstance.addOrUpdateTransientModifier(CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            } else {
                attributeinstance.removeModifier(CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            }
        }

        AttributeInstance attributeinstance1 = this.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (attributeinstance1 != null) {
            if (this.isCreative()) {
                attributeinstance1.addOrUpdateTransientModifier(CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            } else {
                attributeinstance1.removeModifier(CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            }
        }
    }

    public void doTick() {
        try {
            if (!this.isSpectator() || !this.touchingUnloadedChunk()) {
                super.tick();
            }

            for (int i = 0; i < this.getInventory().getContainerSize(); i++) {
                ItemStack itemstack = this.getInventory().getItem(i);
                if (itemstack.getItem().isComplex()) {
                    Packet<?> packet = ((ComplexItem)itemstack.getItem()).getUpdatePacket(itemstack, this.level(), this);
                    if (packet != null) {
                        this.connection.send(packet);
                    }
                }
            }

            if (this.getHealth() != this.lastSentHealth
                || this.lastSentFood != this.foodData.getFoodLevel()
                || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
                this.connection.send(new ClientboundSetHealthPacket(this.getHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel()));
                this.lastSentHealth = this.getHealth();
                this.lastSentFood = this.foodData.getFoodLevel();
                this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
                this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
                this.updateScoreForCriteria(ObjectiveCriteria.HEALTH, Mth.ceil(this.lastRecordedHealthAndAbsorption));
            }

            if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
                this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
                this.updateScoreForCriteria(ObjectiveCriteria.FOOD, Mth.ceil((float)this.lastRecordedFoodLevel));
            }

            if (this.getAirSupply() != this.lastRecordedAirLevel) {
                this.lastRecordedAirLevel = this.getAirSupply();
                this.updateScoreForCriteria(ObjectiveCriteria.AIR, Mth.ceil((float)this.lastRecordedAirLevel));
            }

            if (this.getArmorValue() != this.lastRecordedArmor) {
                this.lastRecordedArmor = this.getArmorValue();
                this.updateScoreForCriteria(ObjectiveCriteria.ARMOR, Mth.ceil((float)this.lastRecordedArmor));
            }

            if (this.totalExperience != this.lastRecordedExperience) {
                this.lastRecordedExperience = this.totalExperience;
                this.updateScoreForCriteria(ObjectiveCriteria.EXPERIENCE, Mth.ceil((float)this.lastRecordedExperience));
            }

            if (this.experienceLevel != this.lastRecordedLevel) {
                this.lastRecordedLevel = this.experienceLevel;
                this.updateScoreForCriteria(ObjectiveCriteria.LEVEL, Mth.ceil((float)this.lastRecordedLevel));
            }

            if (this.totalExperience != this.lastSentExp) {
                this.lastSentExp = this.totalExperience;
                this.connection.send(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
            }

            if (this.getAbilities().flying && !this.mayFly()) {
                this.getAbilities().flying = false;
                this.onUpdateAbilities();
            }

            if (this.tickCount % 20 == 0) {
                CriteriaTriggers.LOCATION.trigger(this);
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking player");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Player being ticked");
            this.fillCrashReportCategory(crashreportcategory);
            throw new ReportedException(crashreport);
        }
    }

    @Override
    public void resetFallDistance() {
        if (this.getHealth() > 0.0F && this.startingToFallPosition != null) {
            CriteriaTriggers.FALL_FROM_HEIGHT.trigger(this, this.startingToFallPosition);
        }

        this.startingToFallPosition = null;
        super.resetFallDistance();
    }

    public void trackStartFallingPosition() {
        if (this.fallDistance > 0.0F && this.startingToFallPosition == null) {
            this.startingToFallPosition = this.position();
            if (this.currentImpulseImpactPos != null) {
                CriteriaTriggers.FALL_AFTER_EXPLOSION.trigger(this, this.currentImpulseImpactPos, this.currentExplosionCause);
            }
        }
    }

    public void trackEnteredOrExitedLavaOnVehicle() {
        if (this.getVehicle() != null && this.getVehicle().isInLava()) {
            if (this.enteredLavaOnVehiclePosition == null) {
                this.enteredLavaOnVehiclePosition = this.position();
            } else {
                CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(this, this.enteredLavaOnVehiclePosition);
            }
        }

        if (this.enteredLavaOnVehiclePosition != null && (this.getVehicle() == null || !this.getVehicle().isInLava())) {
            this.enteredLavaOnVehiclePosition = null;
        }
    }

    private void updateScoreForCriteria(ObjectiveCriteria p_9105_, int p_9106_) {
        this.getScoreboard().forAllObjectives(p_9105_, this, p_313589_ -> p_313589_.set(p_9106_));
    }

    @Override
    public void die(DamageSource p_9035_) {
        this.gameEvent(GameEvent.ENTITY_DIE);
        if (net.neoforged.neoforge.common.CommonHooks.onLivingDeath(this, p_9035_)) return;
        boolean flag = this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
        if (flag) {
            Component component = this.getCombatTracker().getDeathMessage();
            this.connection
                .send(
                    new ClientboundPlayerCombatKillPacket(this.getId(), component),
                    PacketSendListener.exceptionallySend(
                        () -> {
                            int i = 256;
                            String s = component.getString(256);
                            Component component1 = Component.translatable(
                                "death.attack.message_too_long", Component.literal(s).withStyle(ChatFormatting.YELLOW)
                            );
                            Component component2 = Component.translatable("death.attack.even_more_magic", this.getDisplayName())
                                .withStyle(p_143420_ -> p_143420_.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, component1)));
                            return new ClientboundPlayerCombatKillPacket(this.getId(), component2);
                        }
                    )
                );
            Team team = this.getTeam();
            if (team == null || team.getDeathMessageVisibility() == Team.Visibility.ALWAYS) {
                this.server.getPlayerList().broadcastSystemMessage(component, false);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                this.server.getPlayerList().broadcastSystemToTeam(this, component);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, component);
            }
        } else {
            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }

        this.removeEntitiesOnShoulder();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }

        if (!this.isSpectator()) {
            this.dropAllDeathLoot(p_9035_);
        }

        this.getScoreboard().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment);
        LivingEntity livingentity = this.getKillCredit();
        if (livingentity != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(livingentity.getType()));
            livingentity.awardKillScore(this, this.deathScore, p_9035_);
            this.createWitherRose(livingentity);
        }

        this.level().broadcastEntityEvent(this, (byte)3);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
    }

    private void tellNeutralMobsThatIDied() {
        AABB aabb = new AABB(this.blockPosition()).inflate(32.0, 10.0, 32.0);
        this.level()
            .getEntitiesOfClass(Mob.class, aabb, EntitySelector.NO_SPECTATORS)
            .stream()
            .filter(p_9188_ -> p_9188_ instanceof NeutralMob)
            .forEach(p_9057_ -> ((NeutralMob)p_9057_).playerDied(this));
    }

    @Override
    public void awardKillScore(Entity p_9050_, int p_9051_, DamageSource p_9052_) {
        if (p_9050_ != this) {
            super.awardKillScore(p_9050_, p_9051_, p_9052_);
            this.increaseScore(p_9051_);
            this.getScoreboard().forAllObjectives(ObjectiveCriteria.KILL_COUNT_ALL, this, ScoreAccess::increment);
            if (p_9050_ instanceof Player) {
                this.awardStat(Stats.PLAYER_KILLS);
                this.getScoreboard().forAllObjectives(ObjectiveCriteria.KILL_COUNT_PLAYERS, this, ScoreAccess::increment);
            } else {
                this.awardStat(Stats.MOB_KILLS);
            }

            this.handleTeamKill(this, p_9050_, ObjectiveCriteria.TEAM_KILL);
            this.handleTeamKill(p_9050_, this, ObjectiveCriteria.KILLED_BY_TEAM);
            CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(this, p_9050_, p_9052_);
        }
    }

    private void handleTeamKill(ScoreHolder p_313693_, ScoreHolder p_313814_, ObjectiveCriteria[] p_9127_) {
        PlayerTeam playerteam = this.getScoreboard().getPlayersTeam(p_313814_.getScoreboardName());
        if (playerteam != null) {
            int i = playerteam.getColor().getId();
            if (i >= 0 && i < p_9127_.length) {
                this.getScoreboard().forAllObjectives(p_9127_[i], p_313693_, ScoreAccess::increment);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource p_9037_, float p_9038_) {
        if (this.isInvulnerableTo(p_9037_)) {
            return false;
        } else {
            boolean flag = this.server.isDedicatedServer() && this.isPvpAllowed() && p_9037_.is(DamageTypeTags.IS_FALL);
            if (!flag && this.spawnInvulnerableTime > 0 && !p_9037_.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            } else {
                Entity entity = p_9037_.getEntity();
                if (entity instanceof Player player && !this.canHarmPlayer(player)) {
                    return false;
                }

                if (entity instanceof AbstractArrow abstractarrow && abstractarrow.getOwner() instanceof Player player1 && !this.canHarmPlayer(player1)) {
                    return false;
                }

                return super.hurt(p_9037_, p_9038_);
            }
        }
    }

    @Override
    public boolean canHarmPlayer(Player p_9064_) {
        return !this.isPvpAllowed() ? false : super.canHarmPlayer(p_9064_);
    }

    private boolean isPvpAllowed() {
        return this.server.isPvpAllowed();
    }

    @Nullable
    @Override
    protected PortalInfo findDimensionEntryPoint(ServerLevel p_8998_) {
        PortalInfo portalinfo = super.findDimensionEntryPoint(p_8998_);
        if (portalinfo != null && this.level().dimension() == Level.OVERWORLD && p_8998_.dimension() == Level.END) {
            Vec3 vec3 = portalinfo.pos.add(0.0, -1.0, 0.0);
            return new PortalInfo(vec3, Vec3.ZERO, 90.0F, 0.0F);
        } else {
            return portalinfo;
        }
    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel p_9180_, net.neoforged.neoforge.common.util.ITeleporter teleporter) {
        if (!net.neoforged.neoforge.common.CommonHooks.onTravelToDimension(this, p_9180_.dimension())) return null;
        this.isChangingDimension = true;
        ServerLevel serverlevel = this.serverLevel();
        ResourceKey<Level> resourcekey = serverlevel.dimension();
        if (resourcekey == Level.END && p_9180_.dimension() == Level.OVERWORLD && teleporter.isVanilla()) { //Forge: Fix non-vanilla teleporters triggering end credits
            this.unRide();
            this.serverLevel().removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
            if (!this.wonGame) {
                this.wonGame = true;
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, this.seenCredits ? 0.0F : 1.0F));
                this.seenCredits = true;
            }

            return this;
        } else {
            LevelData leveldata = p_9180_.getLevelData();
            this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(p_9180_), (byte)3));
            this.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
            PlayerList playerlist = this.server.getPlayerList();
            playerlist.sendPlayerPermissionLevel(this);
            serverlevel.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
            this.revive();
            PortalInfo portalinfo = teleporter.getPortalInfo(this, p_9180_, this::findDimensionEntryPoint);
            if (portalinfo != null) {
                Entity e = teleporter.placeEntity(this, serverlevel, p_9180_, this.getYRot(), spawnPortal -> {//Forge: Start vanilla logic
                serverlevel.getProfiler().push("moving");
                if (resourcekey == Level.OVERWORLD && p_9180_.dimension() == Level.NETHER) {
                    this.enteredNetherPosition = this.position();
                } else if (spawnPortal && p_9180_.dimension() == Level.END) {
                    this.createEndPlatform(p_9180_, BlockPos.containing(portalinfo.pos));
                }

                serverlevel.getProfiler().pop();
                serverlevel.getProfiler().push("placing");
                this.setServerLevel(p_9180_);
                this.connection.teleport(portalinfo.pos.x, portalinfo.pos.y, portalinfo.pos.z, portalinfo.yRot, portalinfo.xRot);
                this.connection.resetPosition();
                p_9180_.addDuringPortalTeleport(this);
                serverlevel.getProfiler().pop();
                this.triggerDimensionChangeTriggers(serverlevel);
                return this;//forge: this is part of the ITeleporter patch
                });//Forge: End vanilla logic
                if (e != this) throw new java.lang.IllegalArgumentException(String.format(java.util.Locale.ENGLISH, "Teleporter %s returned not the player entity but instead %s, expected PlayerEntity %s", teleporter, e, this));
                this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
                playerlist.sendLevelInfo(this, p_9180_);
                playerlist.sendAllPlayerInfo(this);

                for (MobEffectInstance mobeffectinstance : this.getActiveEffects()) {
                    this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), mobeffectinstance, false));
                }

                if (teleporter.playTeleportSound(this, serverlevel, p_9180_))
                this.connection.send(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
                this.lastSentExp = -1;
                this.lastSentHealth = -1.0F;
                this.lastSentFood = -1;
                net.neoforged.neoforge.event.EventHooks.firePlayerChangedDimensionEvent(this, resourcekey, p_9180_.dimension());
            }

            return this;
        }
    }

    private void createEndPlatform(ServerLevel p_9007_, BlockPos p_9008_) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = p_9008_.mutable();

        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                for (int k = -1; k < 3; k++) {
                    BlockState blockstate = k == -1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    p_9007_.setBlockAndUpdate(blockpos$mutableblockpos.set(p_9008_).move(j, k, i), blockstate);
                }
            }
        }
    }

    @Override
    protected Optional<BlockUtil.FoundRectangle> getExitPortal(ServerLevel p_184131_, BlockPos p_184132_, boolean p_184133_, WorldBorder p_184134_) {
        Optional<BlockUtil.FoundRectangle> optional = super.getExitPortal(p_184131_, p_184132_, p_184133_, p_184134_);
        if (optional.isPresent()) {
            return optional;
        } else {
            Direction.Axis direction$axis = this.level()
                .getBlockState(this.portalEntrancePos)
                .getOptionalValue(NetherPortalBlock.AXIS)
                .orElse(Direction.Axis.X);
            Optional<BlockUtil.FoundRectangle> optional1 = p_184131_.getPortalForcer().createPortal(p_184132_, direction$axis);
            if (optional1.isEmpty()) {
                LOGGER.error("Unable to create a portal, likely target out of worldborder");
            }

            return optional1;
        }
    }

    private void triggerDimensionChangeTriggers(ServerLevel p_9210_) {
        ResourceKey<Level> resourcekey = p_9210_.dimension();
        ResourceKey<Level> resourcekey1 = this.level().dimension();
        CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourcekey, resourcekey1);
        if (resourcekey == Level.NETHER && resourcekey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
            CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (resourcekey1 != Level.NETHER) {
            this.enteredNetherPosition = null;
        }
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer p_9014_) {
        if (p_9014_.isSpectator()) {
            return this.getCamera() == this;
        } else {
            return this.isSpectator() ? false : super.broadcastToPlayer(p_9014_);
        }
    }

    @Override
    public void take(Entity p_9047_, int p_9048_) {
        super.take(p_9047_, p_9048_);
        this.containerMenu.broadcastChanges();
    }

    @Override
    public Either<Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos p_9115_) {
        // Neo: Encapsulate the vanilla check logic to supply to the CanPlayerSleepEvent
        var vanillaResult = ((java.util.function.Supplier<Either<BedSleepingProblem, Unit>>) () -> {
        // Guard against modded beds that may not have the FACING property.
        // We just return success (Unit) here. Modders will need to implement conditions in the CanPlayerSleepEvent
        if (!this.level().getBlockState(p_9115_).hasProperty(HorizontalDirectionalBlock.FACING)) {
            return Either.right(Unit.INSTANCE);
        }

        // Start vanilla code
        Direction direction = this.level().getBlockState(p_9115_).getValue(HorizontalDirectionalBlock.FACING);
        if (this.isSleeping() || !this.isAlive()) {
            return Either.left(Player.BedSleepingProblem.OTHER_PROBLEM);
        } else if (!this.level().dimensionType().natural()) {
            return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        } else if (!this.bedInRange(p_9115_, direction)) {
            return Either.left(Player.BedSleepingProblem.TOO_FAR_AWAY);
        } else if (this.bedBlocked(p_9115_, direction)) {
            return Either.left(Player.BedSleepingProblem.OBSTRUCTED);
        } else {
            this.setRespawnPosition(this.level().dimension(), p_9115_, this.getYRot(), false, true);
            if (this.level().isDay()) {
                return Either.left(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
            } else {
                if (!this.isCreative()) {
                    double d0 = 8.0;
                    double d1 = 5.0;
                    Vec3 vec3 = Vec3.atBottomCenterOf(p_9115_);
                    List<Monster> list = this.level()
                        .getEntitiesOfClass(
                            Monster.class,
                            new AABB(vec3.x() - 8.0, vec3.y() - 5.0, vec3.z() - 8.0, vec3.x() + 8.0, vec3.y() + 5.0, vec3.z() + 8.0),
                            p_9062_ -> p_9062_.isPreventingPlayerRest(this)
                        );
                    if (!list.isEmpty()) {
                        return Either.left(Player.BedSleepingProblem.NOT_SAFE);
                    }
                }
        // End vanilla code
            }
        }
        return Either.right(Unit.INSTANCE);
        }).get();

        // Fire the event. Return the error if one exists after the event, otherwise use the vanilla logic to start sleeping.
        vanillaResult = net.neoforged.neoforge.event.EventHooks.canPlayerStartSleeping(this, p_9115_, vanillaResult);
        if (vanillaResult.left().isPresent()) {
            return vanillaResult;
        }

        {
            {
                // Start vanilla code
                Either<Player.BedSleepingProblem, Unit> either = super.startSleepInBed(p_9115_).ifRight(p_9029_ -> {
                    this.awardStat(Stats.SLEEP_IN_BED);
                    CriteriaTriggers.SLEPT_IN_BED.trigger(this);
                });
                if (!this.serverLevel().canSleepThroughNights()) {
                    this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
                }

                ((ServerLevel)this.level()).updateSleepingPlayerList();
                return either;
            }
        }
    }

    @Override
    public void startSleeping(BlockPos p_9190_) {
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        super.startSleeping(p_9190_);
    }

    private boolean bedInRange(BlockPos p_9117_, Direction p_9118_) {
        if (p_9118_ == null) return false;
        return this.isReachableBedBlock(p_9117_) || this.isReachableBedBlock(p_9117_.relative(p_9118_.getOpposite()));
    }

    private boolean isReachableBedBlock(BlockPos p_9223_) {
        Vec3 vec3 = Vec3.atBottomCenterOf(p_9223_);
        return Math.abs(this.getX() - vec3.x()) <= 3.0 && Math.abs(this.getY() - vec3.y()) <= 2.0 && Math.abs(this.getZ() - vec3.z()) <= 3.0;
    }

    private boolean bedBlocked(BlockPos p_9192_, Direction p_9193_) {
        BlockPos blockpos = p_9192_.above();
        return !this.freeAt(blockpos) || !this.freeAt(blockpos.relative(p_9193_.getOpposite()));
    }

    @Override
    public void stopSleepInBed(boolean p_9165_, boolean p_9166_) {
        if (this.isSleeping()) {
            this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(this, 2));
        }

        super.stopSleepInBed(p_9165_, p_9166_);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        }
    }

    @Override
    public void dismountTo(double p_143389_, double p_143390_, double p_143391_) {
        this.removeVehicle();
        this.setPos(p_143389_, p_143390_, p_143391_);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource p_9182_) {
        return super.isInvulnerableTo(p_9182_) || this.isChangingDimension();
    }

    @Override
    protected void checkFallDamage(double p_8976_, boolean p_8977_, BlockState p_8978_, BlockPos p_8979_) {
    }

    @Override
    protected void onChangedBlock(BlockPos p_9206_) {
        if (!this.isSpectator()) {
            super.onChangedBlock(p_9206_);
        }
    }

    public void doCheckFallDamage(double p_289676_, double p_289671_, double p_289665_, boolean p_289696_) {
        if (!this.touchingUnloadedChunk()) {
            this.checkSupportingBlock(p_289696_, new Vec3(p_289676_, p_289671_, p_289665_));
            BlockPos blockpos = this.getOnPosLegacy();
            BlockState blockstate = this.level().getBlockState(blockpos);
            if (this.spawnExtraParticlesOnFall && p_289696_ && this.fallDistance > 0.0F) {
                Vec3 vec3 = blockpos.getCenter().add(0.0, 0.5, 0.0);
                int i = (int)(50.0F * this.fallDistance);
                this.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockstate), vec3.x, vec3.y, vec3.z, i, 0.3F, 0.3F, 0.3F, 0.15F);
                this.spawnExtraParticlesOnFall = false;
            }

            super.checkFallDamage(p_289671_, p_289696_, blockstate, blockpos);
        }
    }

    @Override
    public void onExplosionHit(@Nullable Entity p_326351_) {
        super.onExplosionHit(p_326351_);
        this.currentImpulseImpactPos = this.position();
        this.currentExplosionCause = p_326351_;
        this.ignoreFallDamageFromCurrentImpulse = p_326351_ != null && p_326351_.getType() == EntityType.WIND_CHARGE;
    }

    @Override
    protected void pushEntities() {
        if (this.level().tickRateManager().runsNormally()) {
            super.pushEntities();
        }
    }

    @Override
    public void openTextEdit(SignBlockEntity p_277909_, boolean p_277495_) {
        this.connection.send(new ClientboundBlockUpdatePacket(this.level(), p_277909_.getBlockPos()));
        this.connection.send(new ClientboundOpenSignEditorPacket(p_277909_.getBlockPos(), p_277495_));
    }

    public void nextContainerCounter() {
        this.containerCounter = this.containerCounter % 100 + 1;
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider p_9033_) {
        return openMenu(p_9033_, (java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf>) null);
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider p_9033_, @Nullable java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extraDataWriter) {
        if (p_9033_ == null) {
            return OptionalInt.empty();
        } else {
            if (this.containerMenu != this.inventoryMenu) {
                if (p_9033_.shouldTriggerClientSideContainerClosingOnOpen())
                this.closeContainer();
                else
                    this.doCloseContainer();
            }

            this.nextContainerCounter();
            AbstractContainerMenu abstractcontainermenu = p_9033_.createMenu(this.containerCounter, this.getInventory(), this);
            if (abstractcontainermenu == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                if (extraDataWriter == null) {
                this.connection
                    .send(new ClientboundOpenScreenPacket(abstractcontainermenu.containerId, abstractcontainermenu.getType(), p_9033_.getDisplayName()));
                } else {
                    this.connection
                        .send(new net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload(abstractcontainermenu.containerId, abstractcontainermenu.getType(), p_9033_.getDisplayName(), net.neoforged.neoforge.common.util.FriendlyByteBufUtil.writeCustomData(extraDataWriter, registryAccess())));
                }
                this.initMenu(abstractcontainermenu);
                this.containerMenu = abstractcontainermenu;
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open(this, this.containerMenu));
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Override
    public void sendMerchantOffers(int p_8988_, MerchantOffers p_8989_, int p_8990_, int p_8991_, boolean p_8992_, boolean p_8993_) {
        this.connection.send(new ClientboundMerchantOffersPacket(p_8988_, p_8989_, p_8990_, p_8991_, p_8992_, p_8993_));
    }

    @Override
    public void openHorseInventory(AbstractHorse p_9059_, Container p_9060_) {
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer();
        }

        this.nextContainerCounter();
        this.connection.send(new ClientboundHorseScreenOpenPacket(this.containerCounter, p_9060_.getContainerSize(), p_9059_.getId()));
        this.containerMenu = new HorseInventoryMenu(this.containerCounter, this.getInventory(), p_9060_, p_9059_);
        this.initMenu(this.containerMenu);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open(this, this.containerMenu));
    }

    @Override
    public void openItemGui(ItemStack p_9082_, InteractionHand p_9083_) {
        if (p_9082_.is(Items.WRITTEN_BOOK)) {
            if (WrittenBookItem.resolveBookComponents(p_9082_, this.createCommandSourceStack(), this)) {
                this.containerMenu.broadcastChanges();
            }

            this.connection.send(new ClientboundOpenBookPacket(p_9083_));
        }
    }

    @Override
    public void openCommandBlock(CommandBlockEntity p_9099_) {
        this.connection.send(ClientboundBlockEntityDataPacket.create(p_9099_, BlockEntity::saveCustomOnly));
    }

    @Override
    public void closeContainer() {
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.doCloseContainer();
    }

    @Override
    public void doCloseContainer() {
        this.containerMenu.removed(this);
        this.inventoryMenu.transferState(this.containerMenu);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close(this, this.containerMenu));
        this.containerMenu = this.inventoryMenu;
    }

    public void setPlayerInput(float p_8981_, float p_8982_, boolean p_8983_, boolean p_8984_) {
        if (this.isPassenger()) {
            if (p_8981_ >= -1.0F && p_8981_ <= 1.0F) {
                this.xxa = p_8981_;
            }

            if (p_8982_ >= -1.0F && p_8982_ <= 1.0F) {
                this.zza = p_8982_;
            }

            this.jumping = p_8983_;
            this.setShiftKeyDown(p_8984_);
        }
    }

    @Override
    public void travel(Vec3 p_308985_) {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        super.travel(p_308985_);
        this.checkMovementStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    @Override
    public void rideTick() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();
        super.rideTick();
        this.checkRidingStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    public void checkMovementStatistics(double p_308996_, double p_309062_, double p_309170_) {
        if (!this.isPassenger() && !didNotMove(p_308996_, p_309062_, p_309170_)) {
            if (this.isSwimming()) {
                int i = Math.round((float)Math.sqrt(p_308996_ * p_308996_ + p_309062_ * p_309062_ + p_309170_ * p_309170_) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.SWIM_ONE_CM, i);
                    this.causeFoodExhaustion(0.01F * (float)i * 0.01F);
                }
            } else if (this.isEyeInFluid(FluidTags.WATER)) {
                int j = Math.round((float)Math.sqrt(p_308996_ * p_308996_ + p_309062_ * p_309062_ + p_309170_ * p_309170_) * 100.0F);
                if (j > 0) {
                    this.awardStat(Stats.WALK_UNDER_WATER_ONE_CM, j);
                    this.causeFoodExhaustion(0.01F * (float)j * 0.01F);
                }
            } else if (this.isInWater()) {
                int k = Math.round((float)Math.sqrt(p_308996_ * p_308996_ + p_309170_ * p_309170_) * 100.0F);
                if (k > 0) {
                    this.awardStat(Stats.WALK_ON_WATER_ONE_CM, k);
                    this.causeFoodExhaustion(0.01F * (float)k * 0.01F);
                }
            } else if (this.onClimbable()) {
                if (p_309062_ > 0.0) {
                    this.awardStat(Stats.CLIMB_ONE_CM, (int)Math.round(p_309062_ * 100.0));
                }
            } else if (this.onGround()) {
                int l = Math.round((float)Math.sqrt(p_308996_ * p_308996_ + p_309170_ * p_309170_) * 100.0F);
                if (l > 0) {
                    if (this.isSprinting()) {
                        this.awardStat(Stats.SPRINT_ONE_CM, l);
                        this.causeFoodExhaustion(0.1F * (float)l * 0.01F);
                    } else if (this.isCrouching()) {
                        this.awardStat(Stats.CROUCH_ONE_CM, l);
                        this.causeFoodExhaustion(0.0F * (float)l * 0.01F);
                    } else {
                        this.awardStat(Stats.WALK_ONE_CM, l);
                        this.causeFoodExhaustion(0.0F * (float)l * 0.01F);
                    }
                }
            } else if (this.isFallFlying()) {
                int i1 = Math.round((float)Math.sqrt(p_308996_ * p_308996_ + p_309062_ * p_309062_ + p_309170_ * p_309170_) * 100.0F);
                this.awardStat(Stats.AVIATE_ONE_CM, i1);
            } else {
                int j1 = Math.round((float)Math.sqrt(p_308996_ * p_308996_ + p_309170_ * p_309170_) * 100.0F);
                if (j1 > 25) {
                    this.awardStat(Stats.FLY_ONE_CM, j1);
                }
            }
        }
    }

    private void checkRidingStatistics(double p_308888_, double p_309131_, double p_308893_) {
        if (this.isPassenger() && !didNotMove(p_308888_, p_309131_, p_308893_)) {
            int i = Math.round((float)Math.sqrt(p_308888_ * p_308888_ + p_309131_ * p_309131_ + p_308893_ * p_308893_) * 100.0F);
            Entity entity = this.getVehicle();
            if (entity instanceof AbstractMinecart) {
                this.awardStat(Stats.MINECART_ONE_CM, i);
            } else if (entity instanceof Boat) {
                this.awardStat(Stats.BOAT_ONE_CM, i);
            } else if (entity instanceof Pig) {
                this.awardStat(Stats.PIG_ONE_CM, i);
            } else if (entity instanceof AbstractHorse) {
                this.awardStat(Stats.HORSE_ONE_CM, i);
            } else if (entity instanceof Strider) {
                this.awardStat(Stats.STRIDER_ONE_CM, i);
            }
        }
    }

    private static boolean didNotMove(double p_309023_, double p_309067_, double p_309143_) {
        return p_309023_ == 0.0 && p_309067_ == 0.0 && p_309143_ == 0.0;
    }

    @Override
    public void awardStat(Stat<?> p_9026_, int p_9027_) {
        this.stats.increment(this, p_9026_, p_9027_);
        this.getScoreboard().forAllObjectives(p_9026_, this, p_313587_ -> p_313587_.add(p_9027_));
    }

    @Override
    public void resetStat(Stat<?> p_9024_) {
        this.stats.setValue(this, p_9024_, 0);
        this.getScoreboard().forAllObjectives(p_9024_, this, ScoreAccess::reset);
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> p_9129_) {
        return this.recipeBook.addRecipes(p_9129_, this);
    }

    @Override
    public void triggerRecipeCrafted(RecipeHolder<?> p_301156_, List<ItemStack> p_282336_) {
        CriteriaTriggers.RECIPE_CRAFTED.trigger(this, p_301156_.id(), p_282336_);
    }

    @Override
    public void awardRecipesByKey(List<ResourceLocation> p_312811_) {
        List<RecipeHolder<?>> list = p_312811_.stream()
            .flatMap(p_311549_ -> this.server.getRecipeManager().byKey(p_311549_).stream())
            .collect(Collectors.toList());
        this.awardRecipes(list);
    }

    @Override
    public int resetRecipes(Collection<RecipeHolder<?>> p_9195_) {
        return this.recipeBook.removeRecipes(p_9195_, this);
    }

    @Override
    public void giveExperiencePoints(int p_9208_) {
        super.giveExperiencePoints(p_9208_);
        this.lastSentExp = -1;
    }

    public void disconnect() {
        this.disconnected = true;
        this.ejectPassengers();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }
    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
    }

    @Override
    public void displayClientMessage(Component p_9154_, boolean p_9155_) {
        this.sendSystemMessage(p_9154_, p_9155_);
    }

    @Override
    protected void completeUsingItem() {
        if (!this.useItem.isEmpty() && this.isUsingItem()) {
            this.connection.send(new ClientboundEntityEventPacket(this, (byte)9));
            super.completeUsingItem();
        }
    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor p_9112_, Vec3 p_9113_) {
        super.lookAt(p_9112_, p_9113_);
        this.connection.send(new ClientboundPlayerLookAtPacket(p_9112_, p_9113_.x, p_9113_.y, p_9113_.z));
    }

    public void lookAt(EntityAnchorArgument.Anchor p_9108_, Entity p_9109_, EntityAnchorArgument.Anchor p_9110_) {
        Vec3 vec3 = p_9110_.apply(p_9109_);
        super.lookAt(p_9108_, vec3);
        this.connection.send(new ClientboundPlayerLookAtPacket(p_9108_, p_9109_, p_9110_));
    }

    public void restoreFrom(ServerPlayer p_9016_, boolean p_9017_) {
        this.wardenSpawnTracker = p_9016_.wardenSpawnTracker;
        this.chatSession = p_9016_.chatSession;
        this.gameMode.setGameModeForPlayer(p_9016_.gameMode.getGameModeForPlayer(), p_9016_.gameMode.getPreviousGameModeForPlayer());
        this.onUpdateAbilities();
        if (p_9017_) {
            this.getInventory().replaceWith(p_9016_.getInventory());
            this.setHealth(p_9016_.getHealth());
            this.foodData = p_9016_.foodData;
            this.experienceLevel = p_9016_.experienceLevel;
            this.totalExperience = p_9016_.totalExperience;
            this.experienceProgress = p_9016_.experienceProgress;
            this.setScore(p_9016_.getScore());
            this.portalEntrancePos = p_9016_.portalEntrancePos;
        } else if (this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || p_9016_.isSpectator()) {
            this.getInventory().replaceWith(p_9016_.getInventory());
            this.experienceLevel = p_9016_.experienceLevel;
            this.totalExperience = p_9016_.totalExperience;
            this.experienceProgress = p_9016_.experienceProgress;
            this.setScore(p_9016_.getScore());
        }

        this.enchantmentSeed = p_9016_.enchantmentSeed;
        this.enderChestInventory = p_9016_.enderChestInventory;
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, p_9016_.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
        this.lastSentExp = -1;
        this.lastSentHealth = -1.0F;
        this.lastSentFood = -1;
        this.recipeBook.copyOverData(p_9016_.recipeBook);
        this.seenCredits = p_9016_.seenCredits;
        this.enteredNetherPosition = p_9016_.enteredNetherPosition;
        this.chunkTrackingView = p_9016_.chunkTrackingView;
        this.setShoulderEntityLeft(p_9016_.getShoulderEntityLeft());
        this.setShoulderEntityRight(p_9016_.getShoulderEntityRight());
        this.setLastDeathLocation(p_9016_.getLastDeathLocation());

        //Copy over a section of the Entity Data from the old player.
        //Allows mods to specify data that persists after players respawn.
        CompoundTag old = p_9016_.getPersistentData();
        if (old.contains(PERSISTED_NBT_TAG))
             getPersistentData().put(PERSISTED_NBT_TAG, old.get(PERSISTED_NBT_TAG));
        net.neoforged.neoforge.event.EventHooks.onPlayerClone(this, p_9016_, !p_9017_);
        this.tabListHeader = p_9016_.tabListHeader;
        this.tabListFooter = p_9016_.tabListFooter;
    }

    @Override
    protected void onEffectAdded(MobEffectInstance p_143393_, @Nullable Entity p_143394_) {
        super.onEffectAdded(p_143393_, p_143394_);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), p_143393_, true));
        if (p_143393_.is(MobEffects.LEVITATION)) {
            this.levitationStartTime = this.tickCount;
            this.levitationStartPos = this.position();
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, p_143394_);
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance p_143396_, boolean p_143397_, @Nullable Entity p_143398_) {
        super.onEffectUpdated(p_143396_, p_143397_, p_143398_);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), p_143396_, false));
        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, p_143398_);
    }

    @Override
    protected void onEffectRemoved(MobEffectInstance p_9184_) {
        super.onEffectRemoved(p_9184_);
        this.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), p_9184_.getEffect()));
        if (p_9184_.is(MobEffects.LEVITATION)) {
            this.levitationStartPos = null;
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, null);
    }

    @Override
    public void teleportTo(double p_8969_, double p_8970_, double p_8971_) {
        this.connection.teleport(p_8969_, p_8970_, p_8971_, this.getYRot(), this.getXRot(), RelativeMovement.ROTATION);
    }

    @Override
    public void teleportRelative(double p_251611_, double p_248861_, double p_252266_) {
        this.connection
            .teleport(this.getX() + p_251611_, this.getY() + p_248861_, this.getZ() + p_252266_, this.getYRot(), this.getXRot(), RelativeMovement.ALL);
    }

    @Override
    public boolean teleportTo(
        ServerLevel p_265564_, double p_265424_, double p_265680_, double p_265312_, Set<RelativeMovement> p_265192_, float p_265059_, float p_265266_
    ) {
        ChunkPos chunkpos = new ChunkPos(BlockPos.containing(p_265424_, p_265680_, p_265312_));
        p_265564_.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkpos, 1, this.getId());
        this.stopRiding();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, true);
        }

        if (p_265564_ == this.level()) {
            this.connection.teleport(p_265424_, p_265680_, p_265312_, p_265059_, p_265266_, p_265192_);
        } else {
            this.teleportTo(p_265564_, p_265424_, p_265680_, p_265312_, p_265059_, p_265266_);
        }

        this.setYHeadRot(p_265059_);
        return true;
    }

    @Override
    public void moveTo(double p_9171_, double p_9172_, double p_9173_) {
        super.moveTo(p_9171_, p_9172_, p_9173_);
        this.connection.resetPosition();
    }

    @Override
    public void crit(Entity p_9045_) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(p_9045_, 4));
    }

    @Override
    public void magicCrit(Entity p_9186_) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(p_9186_, 5));
    }

    @Override
    public void onUpdateAbilities() {
        if (this.connection != null) {
            this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
            this.updateInvisibilityStatus();
        }
    }

    public ServerLevel serverLevel() {
        return (ServerLevel)this.level();
    }

    public boolean setGameMode(GameType p_143404_) {
        p_143404_ = net.neoforged.neoforge.common.CommonHooks.onChangeGameType(this, this.gameMode.getGameModeForPlayer(), p_143404_);
        if (p_143404_ == null) return false;
        if (!this.gameMode.changeGameModeForPlayer(p_143404_)) {
            return false;
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float)p_143404_.getId()));
            if (p_143404_ == GameType.SPECTATOR) {
                this.removeEntitiesOnShoulder();
                this.stopRiding();
            } else {
                this.setCamera(this);
            }

            this.onUpdateAbilities();
            this.updateEffectVisibility();
            return true;
        }
    }

    @Override
    public boolean isSpectator() {
        return this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
    }

    @Override
    public boolean isCreative() {
        return this.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    @Override
    public void sendSystemMessage(Component p_215097_) {
        this.sendSystemMessage(p_215097_, false);
    }

    public void sendSystemMessage(Component p_240560_, boolean p_240545_) {
        if (this.acceptsSystemMessages(p_240545_)) {
            this.connection
                .send(
                    new ClientboundSystemChatPacket(p_240560_, p_240545_),
                    PacketSendListener.exceptionallySend(
                        () -> {
                            if (this.acceptsSystemMessages(false)) {
                                int i = 256;
                                String s = p_240560_.getString(256);
                                Component component = Component.literal(s).withStyle(ChatFormatting.YELLOW);
                                return new ClientboundSystemChatPacket(
                                    Component.translatable("multiplayer.message_not_delivered", component).withStyle(ChatFormatting.RED), false
                                );
                            } else {
                                return null;
                            }
                        }
                    )
                );
        }
    }

    public void sendChatMessage(OutgoingChatMessage p_249852_, boolean p_250110_, ChatType.Bound p_252108_) {
        if (this.acceptsChatMessages()) {
            p_249852_.sendToPlayer(this, p_250110_, p_252108_);
        }
    }

    public String getIpAddress() {
        return this.connection.getRemoteAddress() instanceof InetSocketAddress inetsocketaddress
            ? InetAddresses.toAddrString(inetsocketaddress.getAddress())
            : "<unknown>";
    }

    public void updateOptions(ClientInformation p_301998_) {
        this.language = p_301998_.language();
        this.requestedViewDistance = p_301998_.viewDistance();
        this.chatVisibility = p_301998_.chatVisibility();
        this.canChatColor = p_301998_.chatColors();
        this.textFilteringEnabled = p_301998_.textFilteringEnabled();
        this.allowsListing = p_301998_.allowsListing();
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte)p_301998_.modelCustomisation());
        this.getEntityData().set(DATA_PLAYER_MAIN_HAND, (byte)p_301998_.mainHand().getId());
    }

    public ClientInformation clientInformation() {
        int i = this.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION);
        HumanoidArm humanoidarm = HumanoidArm.BY_ID.apply(this.getEntityData().get(DATA_PLAYER_MAIN_HAND));
        return new ClientInformation(
            this.language, this.requestedViewDistance, this.chatVisibility, this.canChatColor, i, humanoidarm, this.textFilteringEnabled, this.allowsListing
        );
    }

    public boolean canChatInColor() {
        return this.canChatColor;
    }

    public ChatVisiblity getChatVisibility() {
        return this.chatVisibility;
    }

    private boolean acceptsSystemMessages(boolean p_240568_) {
        return this.chatVisibility == ChatVisiblity.HIDDEN ? p_240568_ : true;
    }

    private boolean acceptsChatMessages() {
        return this.chatVisibility == ChatVisiblity.FULL;
    }

    public int requestedViewDistance() {
        return this.requestedViewDistance;
    }

    public void sendServerStatus(ServerStatus p_215110_) {
        this.connection.send(new ClientboundServerDataPacket(p_215110_.description(), p_215110_.favicon().map(ServerStatus.Favicon::iconBytes)));
    }

    @Override
    protected int getPermissionLevel() {
        return this.server.getProfilePermissions(this.getGameProfile());
    }

    public void resetLastActionTime() {
        this.lastActionTime = Util.getMillis();
    }

    public ServerStatsCounter getStats() {
        return this.stats;
    }

    public ServerRecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    @Override
    protected void updateInvisibilityStatus() {
        if (this.isSpectator()) {
            this.removeEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateInvisibilityStatus();
        }
    }

    public Entity getCamera() {
        return (Entity)(this.camera == null ? this : this.camera);
    }

    public void setCamera(@Nullable Entity p_9214_) {
        Entity entity = this.getCamera();
        this.camera = (Entity)(p_9214_ == null ? this : p_9214_);
        while (this.camera instanceof net.neoforged.neoforge.entity.PartEntity<?> partEntity) this.camera = partEntity.getParent(); // Neo: fix MC-46486
        if (entity != this.camera) {
            if (this.camera.level() instanceof ServerLevel serverlevel) {
                this.teleportTo(serverlevel, this.camera.getX(), this.camera.getY(), this.camera.getZ(), Set.of(), this.getYRot(), this.getXRot());
            }

            if (p_9214_ != null) {
                this.serverLevel().getChunkSource().move(this);
            }

            this.connection.send(new ClientboundSetCameraPacket(this.camera));
            this.connection.resetPosition();
        }
    }

    @Override
    protected void processPortalCooldown() {
        if (!this.isChangingDimension) {
            super.processPortalCooldown();
        }
    }

    @Override
    public void attack(Entity p_9220_) {
        if (this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            this.setCamera(p_9220_);
        } else {
            super.attack(p_9220_);
        }
    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    @Nullable
    public Component getTabListDisplayName() {
        if (!this.hasTabListName) {
            this.tabListDisplayName = net.neoforged.neoforge.event.EventHooks.getPlayerTabListDisplayName(this);
            this.hasTabListName = true;
        }
        return this.tabListDisplayName;
    }

    @Override
    public void swing(InteractionHand p_9031_) {
        super.swing(p_9031_);
        this.resetAttackStrengthTicker();
    }

    public boolean isChangingDimension() {
        return this.isChangingDimension;
    }

    public void hasChangedDimension() {
        this.isChangingDimension = false;
    }

    public PlayerAdvancements getAdvancements() {
        return this.advancements;
    }

    public void teleportTo(ServerLevel p_9000_, double p_9001_, double p_9002_, double p_9003_, float p_9004_, float p_9005_) {
        this.setCamera(this);
        this.stopRiding();
        if (p_9000_ == this.level()) {
            this.connection.teleport(p_9001_, p_9002_, p_9003_, p_9004_, p_9005_);
        } else if (net.neoforged.neoforge.common.CommonHooks.onTravelToDimension(this, p_9000_.dimension())) {
            ServerLevel serverlevel = this.serverLevel();
            LevelData leveldata = p_9000_.getLevelData();
            this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(p_9000_), (byte)3));
            this.connection.send(new ClientboundChangeDifficultyPacket(leveldata.getDifficulty(), leveldata.isDifficultyLocked()));
            this.server.getPlayerList().sendPlayerPermissionLevel(this);
            serverlevel.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
            this.revive();
            this.moveTo(p_9001_, p_9002_, p_9003_, p_9004_, p_9005_);
            this.setServerLevel(p_9000_);
            p_9000_.addDuringCommandTeleport(this);
            this.triggerDimensionChangeTriggers(serverlevel);
            this.connection.teleport(p_9001_, p_9002_, p_9003_, p_9004_, p_9005_);
            this.gameMode.setLevel(p_9000_);
            this.server.getPlayerList().sendLevelInfo(this, p_9000_);
            this.server.getPlayerList().sendAllPlayerInfo(this);
            net.neoforged.neoforge.event.EventHooks.firePlayerChangedDimensionEvent(this, serverlevel.dimension(), p_9000_.dimension());
        }
    }

    @Nullable
    public BlockPos getRespawnPosition() {
        return this.respawnPosition;
    }

    public float getRespawnAngle() {
        return this.respawnAngle;
    }

    public ResourceKey<Level> getRespawnDimension() {
        return this.respawnDimension;
    }

    public boolean isRespawnForced() {
        return this.respawnForced;
    }

    public void setRespawnPosition(ResourceKey<Level> p_9159_, @Nullable BlockPos p_9160_, float p_9161_, boolean p_9162_, boolean p_9163_) {
        if (net.neoforged.neoforge.event.EventHooks.onPlayerSpawnSet(this, p_9160_ == null ? Level.OVERWORLD : p_9159_, p_9160_, p_9162_)) return;
        if (p_9160_ != null) {
            boolean flag = p_9160_.equals(this.respawnPosition) && p_9159_.equals(this.respawnDimension);
            if (p_9163_ && !flag) {
                this.sendSystemMessage(Component.translatable("block.minecraft.set_spawn"));
            }

            this.respawnPosition = p_9160_;
            this.respawnDimension = p_9159_;
            this.respawnAngle = p_9161_;
            this.respawnForced = p_9162_;
        } else {
            this.respawnPosition = null;
            this.respawnDimension = Level.OVERWORLD;
            this.respawnAngle = 0.0F;
            this.respawnForced = false;
        }
    }

    public SectionPos getLastSectionPos() {
        return this.lastSectionPos;
    }

    public void setLastSectionPos(SectionPos p_9120_) {
        this.lastSectionPos = p_9120_;
    }

    public ChunkTrackingView getChunkTrackingView() {
        return this.chunkTrackingView;
    }

    public void setChunkTrackingView(ChunkTrackingView p_296310_) {
        this.chunkTrackingView = p_296310_;
    }

    @Override
    public void playNotifySound(SoundEvent p_9019_, SoundSource p_9020_, float p_9021_, float p_9022_) {
        this.connection
            .send(
                new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(p_9019_),
                    p_9020_,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    p_9021_,
                    p_9022_,
                    this.random.nextLong()
                )
            );
    }

    @Override
    public ItemEntity drop(ItemStack p_9085_, boolean p_9086_, boolean p_9087_) {
        ItemEntity itementity = super.drop(p_9085_, p_9086_, p_9087_);
        if (itementity == null) {
            return null;
        } else {
            if (captureDrops() != null) captureDrops().add(itementity);
            else
            this.level().addFreshEntity(itementity);
            ItemStack itemstack = itementity.getItem();
            if (p_9087_) {
                if (!itemstack.isEmpty()) {
                    this.awardStat(Stats.ITEM_DROPPED.get(itemstack.getItem()), p_9085_.getCount());
                }

                this.awardStat(Stats.DROP);
            }

            return itementity;
        }
    }

    /**
     * Returns the language last reported by the player as their local language.
     * Defaults to en_us if the value is unknown.
     */
    public String getLanguage() {
        return this.language;
    }

    private Component tabListHeader = Component.empty();
    private Component tabListFooter = Component.empty();

    public Component getTabListHeader() {
         return this.tabListHeader;
    }

    /**
     * Set the tab list header while preserving the footer.
     *
     * @param header the new header, or {@link Component#empty()} to clear
     */
    public void setTabListHeader(final Component header) {
         this.setTabListHeaderFooter(header, this.tabListFooter);
    }

    public Component getTabListFooter() {
         return this.tabListFooter;
    }

    /**
     * Set the tab list footer while preserving the header.
     *
     * @param footer the new footer, or {@link Component#empty()} to clear
     */
    public void setTabListFooter(final Component footer) {
         this.setTabListHeaderFooter(this.tabListHeader, footer);
    }

    /**
     * Set the tab list header and footer at once.
     *
     * @param header the new header, or {@link Component#empty()} to clear
     * @param footer the new footer, or {@link Component#empty()} to clear
     */
    public void setTabListHeaderFooter(final Component header, final Component footer) {
         if (java.util.Objects.equals(header, this.tabListHeader)
              && java.util.Objects.equals(footer, this.tabListFooter)) {
              return;
         }

         this.tabListHeader = java.util.Objects.requireNonNull(header, "header");
         this.tabListFooter = java.util.Objects.requireNonNull(footer, "footer");

         this.connection.send(new net.minecraft.network.protocol.game.ClientboundTabListPacket(header, footer));
    }

    // We need this as tablistDisplayname may be null even if the event was fired.
    private boolean hasTabListName = false;
    private Component tabListDisplayName = null;
    /**
     * Force the name displayed in the tab list to refresh, by firing {@link net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat}.
     */
    public void refreshTabListName() {
        Component oldName = this.tabListDisplayName;
        this.tabListDisplayName = net.neoforged.neoforge.event.EventHooks.getPlayerTabListDisplayName(this);
        if (!java.util.Objects.equals(oldName, this.tabListDisplayName)) {
            this.getServer().getPlayerList().broadcastAll(new net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, this));
        }
    }

    public TextFilter getTextFilter() {
        return this.textFilter;
    }

    public void setServerLevel(ServerLevel p_284971_) {
        this.setLevel(p_284971_);
        this.gameMode.setLevel(p_284971_);
    }

    @Nullable
    private static GameType readPlayerMode(@Nullable CompoundTag p_143414_, String p_143415_) {
        return p_143414_ != null && p_143414_.contains(p_143415_, 99) ? GameType.byId(p_143414_.getInt(p_143415_)) : null;
    }

    private GameType calculateGameModeForNewPlayer(@Nullable GameType p_143424_) {
        GameType gametype = this.server.getForcedGameType();
        if (gametype != null) {
            return gametype;
        } else {
            return p_143424_ != null ? p_143424_ : this.server.getDefaultGameType();
        }
    }

    public void loadGameTypes(@Nullable CompoundTag p_143428_) {
        this.gameMode
            .setGameModeForPlayer(
                this.calculateGameModeForNewPlayer(readPlayerMode(p_143428_, "playerGameType")), readPlayerMode(p_143428_, "previousPlayerGameType")
            );
    }

    private void storeGameTypes(CompoundTag p_143431_) {
        p_143431_.putInt("playerGameType", this.gameMode.getGameModeForPlayer().getId());
        GameType gametype = this.gameMode.getPreviousGameModeForPlayer();
        if (gametype != null) {
            p_143431_.putInt("previousPlayerGameType", gametype.getId());
        }
    }

    @Override
    public boolean isTextFilteringEnabled() {
        return this.textFilteringEnabled;
    }

    public boolean shouldFilterMessageTo(ServerPlayer p_143422_) {
        return p_143422_ == this ? false : this.textFilteringEnabled || p_143422_.textFilteringEnabled;
    }

    @Override
    public boolean mayInteract(Level p_143406_, BlockPos p_143407_) {
        return super.mayInteract(p_143406_, p_143407_) && p_143406_.mayInteract(this, p_143407_);
    }

    @Override
    protected void updateUsingItem(ItemStack p_143402_) {
        CriteriaTriggers.USING_ITEM.trigger(this, p_143402_);
        super.updateUsingItem(p_143402_);
    }

    public boolean drop(boolean p_182295_) {
        Inventory inventory = this.getInventory();
        ItemStack selected = inventory.getSelected();
        if (selected.isEmpty() || !selected.onDroppedByPlayer(this)) return false;
        if (isUsingItem() && getUsedItemHand() == InteractionHand.MAIN_HAND && (p_182295_ || selected.getCount() == 1)) stopUsingItem(); // Forge: fix MC-231097 on the serverside
        ItemStack itemstack = inventory.removeFromSelected(p_182295_);
        this.containerMenu.findSlot(inventory, inventory.selected).ifPresent(p_287377_ -> this.containerMenu.setRemoteSlot(p_287377_, inventory.getSelected()));
        return net.neoforged.neoforge.common.CommonHooks.onPlayerTossEvent(this, itemstack, true) != null;
    }

    public boolean allowsListing() {
        return this.allowsListing;
    }

    @Override
    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.of(this.wardenSpawnTracker);
    }

    public void setSpawnExtraParticlesOnFall(boolean p_334029_) {
        this.spawnExtraParticlesOnFall = p_334029_;
    }

    @Override
    public void onItemPickup(ItemEntity p_215095_) {
        super.onItemPickup(p_215095_);
        Entity entity = p_215095_.getOwner();
        if (entity != null) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.trigger(this, p_215095_.getItem(), entity);
        }
    }

    public void setChatSession(RemoteChatSession p_254468_) {
        this.chatSession = p_254468_;
    }

    @Nullable
    public RemoteChatSession getChatSession() {
        return this.chatSession != null && this.chatSession.hasExpired() ? null : this.chatSession;
    }

    @Override
    public void indicateDamage(double p_270621_, double p_270478_) {
        this.hurtDir = (float)(Mth.atan2(p_270478_, p_270621_) * 180.0F / (float)Math.PI - (double)this.getYRot());
        this.connection.send(new ClientboundHurtAnimationPacket(this));
    }

    @Override
    public boolean startRiding(Entity p_277395_, boolean p_278062_) {
        if (!super.startRiding(p_277395_, p_278062_)) {
            return false;
        } else {
            p_277395_.positionRider(this);
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
            if (p_277395_ instanceof LivingEntity livingentity) {
                for (MobEffectInstance mobeffectinstance : livingentity.getActiveEffects()) {
                    this.connection.send(new ClientboundUpdateMobEffectPacket(p_277395_.getId(), mobeffectinstance, false));
                }
            }

            return true;
        }
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();
        super.stopRiding();
        if (entity instanceof LivingEntity livingentity) {
            for (MobEffectInstance mobeffectinstance : livingentity.getActiveEffects()) {
                this.connection.send(new ClientboundRemoveMobEffectPacket(entity.getId(), mobeffectinstance.getEffect()));
            }
        }
    }

    public CommonPlayerSpawnInfo createCommonSpawnInfo(ServerLevel p_294169_) {
        return new CommonPlayerSpawnInfo(
            p_294169_.dimensionTypeRegistration(),
            p_294169_.dimension(),
            BiomeManager.obfuscateSeed(p_294169_.getSeed()),
            this.gameMode.getGameModeForPlayer(),
            this.gameMode.getPreviousGameModeForPlayer(),
            p_294169_.isDebug(),
            p_294169_.isFlat(),
            this.getLastDeathLocation(),
            this.getPortalCooldown()
        );
    }

    public void setRaidOmenPosition(BlockPos p_338782_) {
        this.raidOmenPosition = p_338782_;
    }

    public void clearRaidOmenPosition() {
        this.raidOmenPosition = null;
    }

    @Nullable
    public BlockPos getRaidOmenPosition() {
        return this.raidOmenPosition;
    }
}
