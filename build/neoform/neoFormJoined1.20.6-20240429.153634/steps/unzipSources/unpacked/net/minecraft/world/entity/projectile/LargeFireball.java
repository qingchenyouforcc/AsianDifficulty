package net.minecraft.world.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class LargeFireball extends Fireball {
    private int explosionPower = 1;

    public LargeFireball(EntityType<? extends LargeFireball> p_37199_, Level p_37200_) {
        super(p_37199_, p_37200_);
    }

    public LargeFireball(Level p_181151_, LivingEntity p_181152_, double p_181153_, double p_181154_, double p_181155_, int p_181156_) {
        super(EntityType.FIREBALL, p_181152_, p_181153_, p_181154_, p_181155_, p_181151_);
        this.explosionPower = p_181156_;
    }

    @Override
    protected void onHit(HitResult p_37218_) {
        super.onHit(p_37218_);
        if (!this.level().isClientSide) {
            // TODO 1.19.3: The creation of Level.ExplosionInteraction means this code path will fire EntityMobGriefingEvent twice. Should we try and fix it? -SS
            boolean flag = net.neoforged.neoforge.event.EventHooks.canEntityGrief(this.level(), this.getOwner());
            this.level().explode(this, this.getX(), this.getY(), this.getZ(), (float)this.explosionPower, flag, Level.ExplosionInteraction.MOB);
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult p_37216_) {
        super.onHitEntity(p_37216_);
        if (!this.level().isClientSide) {
            Entity entity = p_37216_.getEntity();
            Entity entity1 = this.getOwner();
            entity.hurt(this.damageSources().fireball(this, entity1), 6.0F);
            if (entity1 instanceof LivingEntity) {
                this.doEnchantDamageEffects((LivingEntity)entity1, entity);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag p_37222_) {
        super.addAdditionalSaveData(p_37222_);
        p_37222_.putByte("ExplosionPower", (byte)this.explosionPower);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag p_37220_) {
        super.readAdditionalSaveData(p_37220_);
        if (p_37220_.contains("ExplosionPower", 99)) {
            this.explosionPower = p_37220_.getByte("ExplosionPower");
        }
    }
}
