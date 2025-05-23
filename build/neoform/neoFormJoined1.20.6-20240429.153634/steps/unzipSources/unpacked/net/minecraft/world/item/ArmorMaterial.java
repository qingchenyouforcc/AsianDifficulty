package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.crafting.Ingredient;

public record ArmorMaterial(
    Map<ArmorItem.Type, Integer> defense,
    int enchantmentValue,
    Holder<SoundEvent> equipSound,
    Supplier<Ingredient> repairIngredient,
    List<ArmorMaterial.Layer> layers,
    float toughness,
    float knockbackResistance
) {
    public static final Codec<Holder<ArmorMaterial>> CODEC = BuiltInRegistries.ARMOR_MATERIAL.holderByNameCodec();

    public int getDefense(ArmorItem.Type p_324094_) {
        return this.defense.getOrDefault(p_324094_, 0);
    }

    public static final class Layer {
        private final ResourceLocation assetName;
        private final String suffix;
        private final boolean dyeable;
        private final ResourceLocation innerTexture;
        private final ResourceLocation outerTexture;

        public Layer(ResourceLocation p_324274_, String p_324446_, boolean p_324406_) {
            this.assetName = p_324274_;
            this.suffix = p_324446_;
            this.dyeable = p_324406_;
            this.innerTexture = this.resolveTexture(true);
            this.outerTexture = this.resolveTexture(false);
        }

        public Layer(ResourceLocation p_323826_) {
            this(p_323826_, "", false);
        }

        private ResourceLocation resolveTexture(boolean p_324124_) {
            return this.assetName
                .withPath(p_324187_ -> "textures/models/armor/" + this.assetName.getPath() + "_layer_" + (p_324124_ ? 2 : 1) + this.suffix + ".png");
        }

        public ResourceLocation texture(boolean p_323901_) {
            return p_323901_ ? this.innerTexture : this.outerTexture;
        }

        public boolean dyeable() {
            return this.dyeable;
        }
    }
}
