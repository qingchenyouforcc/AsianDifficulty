package net.mcreator.asiandifficulty.procedures;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.Event;

import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

import net.mcreator.asiandifficulty.init.AsianDifficultyModMobEffects;
import net.mcreator.asiandifficulty.AsianDifficultyMod;

import javax.annotation.Nullable;

@EventBusSubscriber
public class PlayerFluEventProcedure {
	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		execute(event, event.getEntity().level(), event.getEntity());
	}

	public static void execute(LevelAccessor world, Entity entity) {
		execute(null, world, entity);
	}

	private static void execute(@Nullable Event event, LevelAccessor world, Entity entity) {
		if (entity == null)
			return;
		double timer = 0;
		if (new Object() {
			public boolean checkGamemode(Entity _ent) {
				if (_ent instanceof ServerPlayer _serverPlayer) {
					return _serverPlayer.gameMode.getGameModeForPlayer() == GameType.SURVIVAL;
				} else if (_ent.level().isClientSide() && _ent instanceof Player _player) {
					return Minecraft.getInstance().getConnection().getPlayerInfo(_player.getGameProfile().getId()) != null && Minecraft.getInstance().getConnection().getPlayerInfo(_player.getGameProfile().getId()).getGameMode() == GameType.SURVIVAL;
				}
				return false;
			}
		}.checkGamemode(entity)) {
			if (new Object() {
				public int getScore(String score, Entity _ent) {
					Scoreboard _sc = _ent.level().getScoreboard();
					Objective _so = _sc.getObjective(score);
					if (_so != null)
						return _sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).get();
					return 0;
				}
			}.getScore("cold_enable", entity) == 0) {
				if (entity.isInWaterRainOrBubble()) {
					if (entity instanceof Player _player && !_player.level().isClientSide())
						_player.displayClientMessage(Component.literal("You are in the water and it is easy to catch a cold"), true);
					{
						Entity _ent = entity;
						Scoreboard _sc = _ent.level().getScoreboard();
						Objective _so = _sc.getObjective("cold_tick");
						if (_so == null)
							_so = _sc.addObjective("cold_tick", ObjectiveCriteria.DUMMY, Component.literal("cold_tick"), ObjectiveCriteria.RenderType.INTEGER, true, null);
						_sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).set((int) (new Object() {
							public int getScore(String score, Entity _ent) {
								Scoreboard _sc = _ent.level().getScoreboard();
								Objective _so = _sc.getObjective(score);
								if (_so != null)
									return _sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).get();
								return 0;
							}
						}.getScore("cold_tick", entity) + 1));
					}
				}
				if (new Object() {
					public int getScore(String score, Entity _ent) {
						Scoreboard _sc = _ent.level().getScoreboard();
						Objective _so = _sc.getObjective(score);
						if (_so != null)
							return _sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).get();
						return 0;
					}
				}.getScore("cold_tick", entity) > 2400) {
					if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
						_entity.addEffect(new MobEffectInstance(AsianDifficultyModMobEffects.FLU_EFFECT, 12000, 1));
					if (entity instanceof LivingEntity _entity && !_entity.level().isClientSide())
						_entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 12000, 1));
					{
						Entity _ent = entity;
						Scoreboard _sc = _ent.level().getScoreboard();
						Objective _so = _sc.getObjective("cold_tick");
						if (_so == null)
							_so = _sc.addObjective("cold_tick", ObjectiveCriteria.DUMMY, Component.literal("cold_tick"), ObjectiveCriteria.RenderType.INTEGER, true, null);
						_sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).set(0);
					}
					{
						Entity _ent = entity;
						Scoreboard _sc = _ent.level().getScoreboard();
						Objective _so = _sc.getObjective("cold_enable");
						if (_so == null)
							_so = _sc.addObjective("cold_enable", ObjectiveCriteria.DUMMY, Component.literal("cold_enable"), ObjectiveCriteria.RenderType.INTEGER, true, null);
						_sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).set(1);
					}
					AsianDifficultyMod.queueServerWork(12000, () -> {
						{
							Entity _ent = entity;
							Scoreboard _sc = _ent.level().getScoreboard();
							Objective _so = _sc.getObjective("cold_enable");
							if (_so == null)
								_so = _sc.addObjective("cold_enable", ObjectiveCriteria.DUMMY, Component.literal("cold_enable"), ObjectiveCriteria.RenderType.INTEGER, true, null);
							_sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).set(0);
						}
					});
				}
			} else {
				if (entity instanceof LivingEntity _livEnt13 && _livEnt13.hasEffect(AsianDifficultyModMobEffects.FLU_EFFECT)) {
				} else {
					{
						Entity _ent = entity;
						Scoreboard _sc = _ent.level().getScoreboard();
						Objective _so = _sc.getObjective("cold_enable");
						if (_so == null)
							_so = _sc.addObjective("cold_enable", ObjectiveCriteria.DUMMY, Component.literal("cold_enable"), ObjectiveCriteria.RenderType.INTEGER, true, null);
						_sc.getOrCreatePlayerScore(ScoreHolder.forNameOnly(_ent.getScoreboardName()), _so).set(0);
					}
				}
			}
		}
	}
}
