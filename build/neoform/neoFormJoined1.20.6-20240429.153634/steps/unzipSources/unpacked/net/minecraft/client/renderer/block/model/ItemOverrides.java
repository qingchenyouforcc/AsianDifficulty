package net.minecraft.client.renderer.block.model;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemOverrides {
    public static final ItemOverrides EMPTY = new ItemOverrides();
    public static final float NO_OVERRIDE = Float.NEGATIVE_INFINITY;
    private final ItemOverrides.BakedOverride[] overrides;
    private final ResourceLocation[] properties;

    protected ItemOverrides() {
        this.overrides = new ItemOverrides.BakedOverride[0];
        this.properties = new ResourceLocation[0];
    }

    /**
     * @deprecated Forge: Use {@link #ItemOverrides(ModelBaker, UnbakedModel, List, java.util.function.Function)}
     */
    @Deprecated
    public ItemOverrides(ModelBaker p_251211_, BlockModel p_111741_, List<ItemOverride> p_111743_) {
        this(p_251211_, p_111741_, p_111743_, p_251211_.getModelTextureGetter());
    }

    public ItemOverrides(ModelBaker p_251211_, UnbakedModel p_111741_, List<ItemOverride> p_111743_, java.util.function.Function<net.minecraft.client.resources.model.Material, net.minecraft.client.renderer.texture.TextureAtlasSprite> spriteGetter) {
        this.properties = p_111743_.stream()
            .flatMap(ItemOverride::getPredicates)
            .map(ItemOverride.Predicate::getProperty)
            .distinct()
            .toArray(ResourceLocation[]::new);
        Object2IntMap<ResourceLocation> object2intmap = new Object2IntOpenHashMap<>();

        for (int i = 0; i < this.properties.length; i++) {
            object2intmap.put(this.properties[i], i);
        }

        List<ItemOverrides.BakedOverride> list = Lists.newArrayList();

        for (int j = p_111743_.size() - 1; j >= 0; j--) {
            ItemOverride itemoverride = p_111743_.get(j);
            BakedModel bakedmodel = this.bakeModel(p_251211_, p_111741_, itemoverride, spriteGetter);
            ItemOverrides.PropertyMatcher[] aitemoverrides$propertymatcher = itemoverride.getPredicates().map(p_173477_ -> {
                int k = object2intmap.getInt(p_173477_.getProperty());
                return new ItemOverrides.PropertyMatcher(k, p_173477_.getValue());
            }).toArray(ItemOverrides.PropertyMatcher[]::new);
            list.add(new ItemOverrides.BakedOverride(aitemoverrides$propertymatcher, bakedmodel));
        }

        this.overrides = list.toArray(new ItemOverrides.BakedOverride[0]);
    }

    @Nullable
    private BakedModel bakeModel(ModelBaker p_249483_, UnbakedModel p_251965_, ItemOverride p_250816_, java.util.function.Function<net.minecraft.client.resources.model.Material, net.minecraft.client.renderer.texture.TextureAtlasSprite> spriteGetter) {
        UnbakedModel unbakedmodel = p_249483_.getModel(p_250816_.getModel());
        return Objects.equals(unbakedmodel, p_251965_) ? null : p_249483_.bake(p_250816_.getModel(), BlockModelRotation.X0_Y0, spriteGetter);
    }

    @Nullable
    public BakedModel resolve(BakedModel p_173465_, ItemStack p_173466_, @Nullable ClientLevel p_173467_, @Nullable LivingEntity p_173468_, int p_173469_) {
        if (this.overrides.length != 0) {
            int i = this.properties.length;
            float[] afloat = new float[i];

            for (int j = 0; j < i; j++) {
                ResourceLocation resourcelocation = this.properties[j];
                ItemPropertyFunction itempropertyfunction = ItemProperties.getProperty(p_173466_, resourcelocation);
                if (itempropertyfunction != null) {
                    afloat[j] = itempropertyfunction.call(p_173466_, p_173467_, p_173468_, p_173469_);
                } else {
                    afloat[j] = Float.NEGATIVE_INFINITY;
                }
            }

            for (ItemOverrides.BakedOverride itemoverrides$bakedoverride : this.overrides) {
                if (itemoverrides$bakedoverride.test(afloat)) {
                    BakedModel bakedmodel = itemoverrides$bakedoverride.model;
                    if (bakedmodel == null) {
                        return p_173465_;
                    }

                    return bakedmodel;
                }
            }
        }

        return p_173465_;
    }

    public com.google.common.collect.ImmutableList<BakedOverride> getOverrides() {
        return com.google.common.collect.ImmutableList.copyOf(overrides);
    }

    @OnlyIn(Dist.CLIENT)
    public static class BakedOverride {
        private final ItemOverrides.PropertyMatcher[] matchers;
        @Nullable
        final BakedModel model;

        BakedOverride(ItemOverrides.PropertyMatcher[] p_173483_, @Nullable BakedModel p_173484_) {
            this.matchers = p_173483_;
            this.model = p_173484_;
        }

        boolean test(float[] p_173486_) {
            for (ItemOverrides.PropertyMatcher itemoverrides$propertymatcher : this.matchers) {
                float f = p_173486_[itemoverrides$propertymatcher.index];
                if (f < itemoverrides$propertymatcher.value) {
                    return false;
                }
            }

            return true;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class PropertyMatcher {
        public final int index;
        public final float value;

        PropertyMatcher(int p_173490_, float p_173491_) {
            this.index = p_173490_;
            this.value = p_173491_;
        }
    }
}
