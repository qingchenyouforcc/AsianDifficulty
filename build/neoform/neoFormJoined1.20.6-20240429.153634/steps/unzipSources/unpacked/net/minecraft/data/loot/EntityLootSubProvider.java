package net.minecraft.data.loot;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.minecraft.advancements.critereon.DamageSourcePredicate;
import net.minecraft.advancements.critereon.EntityFlagsPredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.EntitySubPredicates;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.DamageSourceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

public abstract class EntityLootSubProvider implements LootTableSubProvider {
    protected static final EntityPredicate.Builder ENTITY_ON_FIRE = EntityPredicate.Builder.entity()
        .flags(EntityFlagsPredicate.Builder.flags().setOnFire(true));
    private static final Set<EntityType<?>> SPECIAL_LOOT_TABLE_TYPES = ImmutableSet.of(
        EntityType.PLAYER, EntityType.ARMOR_STAND, EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.VILLAGER
    );
    private final FeatureFlagSet allowed;
    private final FeatureFlagSet required;
    private final Map<EntityType<?>, Map<ResourceKey<LootTable>, LootTable.Builder>> map = Maps.newHashMap();

    protected EntityLootSubProvider(FeatureFlagSet p_251971_) {
        this(p_251971_, p_251971_);
    }

    protected EntityLootSubProvider(FeatureFlagSet p_266989_, FeatureFlagSet p_267138_) {
        this.allowed = p_266989_;
        this.required = p_267138_;
    }

    protected static LootTable.Builder createSheepTable(ItemLike p_249422_) {
        return LootTable.lootTable()
            .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(LootItem.lootTableItem(p_249422_)))
            .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0F)).add(NestedLootTable.lootTableReference(EntityType.SHEEP.getDefaultLootTable())));
    }

    public abstract void generate();

    protected java.util.stream.Stream<EntityType<?>> getKnownEntityTypes() {
        return BuiltInRegistries.ENTITY_TYPE.stream();
    }

    @Override
    public void generate(HolderLookup.Provider p_331660_, BiConsumer<ResourceKey<LootTable>, LootTable.Builder> p_251751_) {
        this.generate();
        Set<ResourceKey<LootTable>> set = new HashSet<>();
        this.getKnownEntityTypes()
            .map(EntityType::builtInRegistryHolder)
            .forEach(
                p_266624_ -> {
                    EntityType<?> entitytype = p_266624_.value();
                    if (entitytype.isEnabled(this.allowed)) {
                        if (canHaveLootTable(entitytype)) {
                            Map<ResourceKey<LootTable>, LootTable.Builder> map = this.map.remove(entitytype);
                            ResourceKey<LootTable> resourcekey = entitytype.getDefaultLootTable();
                            if (resourcekey != BuiltInLootTables.EMPTY && entitytype.isEnabled(this.required) && (map == null || !map.containsKey(resourcekey))
                                )
                             {
                                throw new IllegalStateException(
                                    String.format(Locale.ROOT, "Missing loottable '%s' for '%s'", resourcekey, p_266624_.key().location())
                                );
                            }

                            if (map != null) {
                                map.forEach(
                                    (p_335546_, p_250972_) -> {
                                        if (!set.add((ResourceKey<LootTable>)p_335546_)) {
                                            throw new IllegalStateException(
                                                String.format(Locale.ROOT, "Duplicate loottable '%s' for '%s'", p_335546_, p_266624_.key().location())
                                            );
                                        } else {
                                            p_251751_.accept((ResourceKey<LootTable>)p_335546_, p_250972_);
                                        }
                                    }
                                );
                            }
                        } else {
                            Map<ResourceKey<LootTable>, LootTable.Builder> map1 = this.map.remove(entitytype);
                            if (map1 != null) {
                                throw new IllegalStateException(
                                    String.format(
                                        Locale.ROOT,
                                        "Weird loottables '%s' for '%s', not a LivingEntity so should not have loot",
                                        map1.keySet().stream().map(p_335190_ -> p_335190_.location().toString()).collect(Collectors.joining(",")),
                                        p_266624_.key().location()
                                    )
                                );
                            }
                        }
                    }
                }
            );
        if (!this.map.isEmpty()) {
            throw new IllegalStateException("Created loot tables for entities not supported by datapack: " + this.map.keySet());
        }
    }

    protected boolean canHaveLootTable(EntityType<?> p_249029_) {
        return SPECIAL_LOOT_TABLE_TYPES.contains(p_249029_) || p_249029_.getCategory() != MobCategory.MISC;
    }

    protected LootItemCondition.Builder killedByFrog() {
        return DamageSourceCondition.hasDamageSource(DamageSourcePredicate.Builder.damageType().source(EntityPredicate.Builder.entity().of(EntityType.FROG)));
    }

    protected LootItemCondition.Builder killedByFrogVariant(ResourceKey<FrogVariant> p_335676_) {
        return DamageSourceCondition.hasDamageSource(
            DamageSourcePredicate.Builder.damageType()
                .source(
                    EntityPredicate.Builder.entity()
                        .of(EntityType.FROG)
                        .subPredicate(EntitySubPredicates.frogVariant(BuiltInRegistries.FROG_VARIANT.getHolderOrThrow(p_335676_)))
                )
        );
    }

    protected void add(EntityType<?> p_248740_, LootTable.Builder p_249440_) {
        this.add(p_248740_, p_248740_.getDefaultLootTable(), p_249440_);
    }

    protected void add(EntityType<?> p_252130_, ResourceKey<LootTable> p_335943_, LootTable.Builder p_249357_) {
        this.map.computeIfAbsent(p_252130_, p_251466_ -> new HashMap<>()).put(p_335943_, p_249357_);
    }
}
