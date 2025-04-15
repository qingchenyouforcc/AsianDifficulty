
/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.asiandifficulty.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.registries.Registries;

import net.mcreator.asiandifficulty.potion.FluEffectMobEffect;
import net.mcreator.asiandifficulty.AsianDifficultyMod;

public class AsianDifficultyModMobEffects {
	public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(Registries.MOB_EFFECT, AsianDifficultyMod.MODID);
	public static final DeferredHolder<MobEffect, MobEffect> FLU_EFFECT = REGISTRY.register("flu_effect", () -> new FluEffectMobEffect());
}
