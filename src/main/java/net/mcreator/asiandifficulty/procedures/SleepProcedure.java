package net.mcreator.asiandifficulty.procedures;

import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.Event;

import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

@EventBusSubscriber
public class SleepProcedure {
	@SubscribeEvent
	public static void onEntityEndSleep(PlayerWakeUpEvent event) {
		execute(event, event.getEntity());
	}

	public static void execute(Entity entity) {
		execute(null, entity);
	}

	private static void execute(@Nullable Event event, Entity entity) {
		if (entity == null)
			return;
		{
			Entity _ent = entity;
			Scoreboard _sc = _ent.level().getScoreboard();
			Objective _so = _sc.getObjective("shiftDeath");
			if (_so == null)
				_so = _sc.addObjective("shiftDeath", ObjectiveCriteria.DUMMY, Component.literal("shiftDeath"), ObjectiveCriteria.RenderType.INTEGER, true, null);
			_sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).set(0);
		}
		{
			Entity _ent = entity;
			Scoreboard _sc = _ent.level().getScoreboard();
			Objective _so = _sc.getObjective("jumpDeath");
			if (_so == null)
				_so = _sc.addObjective("jumpDeath", ObjectiveCriteria.DUMMY, Component.literal("jumpDeath"), ObjectiveCriteria.RenderType.INTEGER, true, null);
			_sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).set(0);
		}
	}
}
