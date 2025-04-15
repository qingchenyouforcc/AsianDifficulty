
package net.mcreator.asiandifficulty.potion;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;

import net.mcreator.asiandifficulty.procedures.FluEffectRunProcedure;

public class FluEffectMobEffect extends MobEffect {
	public FluEffectMobEffect() {
		super(MobEffectCategory.HARMFUL, -13369549);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		FluEffectRunProcedure.execute(entity.level(), entity);
		return super.applyEffectTick(entity, amplifier);
	}
}
