package com.minenash.action_hunger.mixin;

import com.minenash.action_hunger.ActionHunger;
import com.minenash.action_hunger.config.Config;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {

    @Shadow private int foodLevel;
    @Shadow private float exhaustion;
    @Shadow private float saturationLevel;
    @Shadow private int foodTickTimer;
    @Shadow public abstract void addExhaustion(float exhaustion);
    @Shadow private void addInternal(int food, float exhaustion) {}

    @Unique private int constantRegenTimer = 0;
    @Unique private int constantHungerTimer = 0;
    @Unique private int shieldExhaustionTimer = 0;


    @Redirect(method = "eat", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;addInternal(IF)V"))
    private void actionHunger$modifyFoodComponent(HungerManager manager, int food, float saturation) {
        addInternal(Math.round(food * Config.hungerFromFoodMultiplier), saturation * Config.saturationFromFoodMultiplier);
    }

    /**
     * @author Minenash
     * @reason Too complicated to with normal mixins
     */
    @Overwrite
    public void update(PlayerEntity player) {
        Difficulty difficulty = player.getWorld().getDifficulty();
        if (this.exhaustion > 4.0F) {
            this.exhaustion -= 4.0F;
            if (this.saturationLevel > 0.0F) {
                this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
            } else if (difficulty != Difficulty.PEACEFUL) {
                this.foodLevel = Math.max(this.foodLevel - 1, 0);
            }
        }

        if (player.getAbilities().invulnerable)
            return;

        double dynamicRegenRateModifier = ActionHunger.getCurveModifier(player.getHealth(), Config.dynamicRegenRateCurve, Config.dynamicRegenRateMultiplier);


        boolean isPlayerUsingShield = player.getActiveItem().getItem() == Items.SHIELD;

        if (isPlayerUsingShield) {
            ++this.shieldExhaustionTimer;
            if (this.shieldExhaustionTimer >= Config.shieldExhaustionRate) {
                if (Config.debug)
                    System.out.println("Exhaustion from " + "Shield" + ": " + Config.shieldExhaustionAmount);
                this.addExhaustion(Config.shieldExhaustionAmount);
            }
        } else
            this.shieldExhaustionTimer = 0;

        boolean regened = true;

        boolean blockRegenFromShield = Config.disableRegenWhenUsingShield && isPlayerUsingShield;
        boolean bl = player.getWorld().getGameRules().getBoolean(GameRules.NATURAL_REGENERATION);
        if (bl && !blockRegenFromShield) {
            constantRegenTimer++;
            if (constantRegenTimer >= Config.constantRegenRate * (Config.dynamicRegenOnConstantRegen ? dynamicRegenRateModifier : 1.0D)) {
                if (Config.debug)
                    System.out.println("Heal from " + "Const" + ": " + Config.constantRegenAmount);
                player.heal(Config.constantRegenAmount);
                constantRegenTimer = 0;
            }

            if (this.saturationLevel > 0.0F && player.canFoodHeal() && this.foodLevel >= Config.hyperFoodRegenMinimumHunger) {
                ++this.foodTickTimer;
                if (this.foodTickTimer >= Config.hyperFoodRegenRate * (Config.dynamicRegenOnHyperFoodRegen ? dynamicRegenRateModifier : 1.0D)) {
                    float f = Math.min(this.saturationLevel, 6.0F);
                    if (Config.debug)
                        System.out.println("Heal from " + "Hyper" + ": " + f / 6.0F * Config.hyperFoodRegenHealthMultiplier);
                    player.heal(f / 6.0F * Config.hyperFoodRegenHealthMultiplier);
                    if (Config.debug)
                        System.out.println("Exhaustion from " + "Hyper" + ": " + f * Config.hyperFoodRegenExhaustionMultiplier);
                    this.addExhaustion(f * Config.hyperFoodRegenExhaustionMultiplier);
                    this.foodTickTimer = 0;
                }
            } else if (this.foodLevel >= Config.foodRegenMinimumHunger && player.canFoodHeal()) {
                ++this.foodTickTimer;
                if (this.foodTickTimer >= Config.foodRegenRate * (Config.dynamicRegenOnFoodRegen ? dynamicRegenRateModifier : 1.0D)) {
                    if (Config.debug)
                        System.out.println("Heal from " + "Food" + ": " + Config.foodRegenHealthAmount);
                    player.heal(Config.foodRegenHealthAmount);
                    if (Config.debug)
                        System.out.println("Exhaustion from " + "Food" + ": " + Config.foodRegenExhaustionAmount);
                    this.addExhaustion(Config.foodRegenExhaustionAmount);
                    this.foodTickTimer = 0;
                }
            } else {
                regened = false;
            }
        }

        this.constantHungerTimer++;
        if (this.constantHungerTimer >= Config.constantExhaustionRate * (Config.dynamicRegenOnConstantExhaustion ? dynamicRegenRateModifier : 1.0D)) {
            if (Config.debug)
                System.out.println("Exhaustion from " + "Const" + ": " + Config.constantExhaustionAmount);
            this.addExhaustion(Config.constantExhaustionAmount);
            this.constantHungerTimer = 0;
        }

        if (this.foodLevel <= 0) {
            ++this.foodTickTimer;
            if (this.foodTickTimer >= Config.starvationDamageRate) {
                if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD || player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL)
                    player.damage(player.getDamageSources().starve(), Config.starvationDamageAmount);
                this.foodTickTimer = 0;
            }
        } else if (!regened){
            this.foodTickTimer = 0;
        }

    }


}
