package com.aetherteam.aether.mixin.mixins.common;

import com.aetherteam.aether.entity.passive.MountableAnimal;
import com.aetherteam.aether.event.hooks.AbilityHooks;
import com.aetherteam.aether.item.combat.loot.ValkyrieLanceItem;
import com.aetherteam.aether.mixin.AetherMixinHooks;
import com.aetherteam.aetherfabric.events.CancellableCallbackImpl;
import com.aetherteam.aetherfabric.events.LivingEntityEvents;
import com.aetherteam.aetherfabric.events.PlayerEvents;
import com.aetherteam.aetherfabric.events.PlayerTickEvents;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Shadow
    public abstract void resetAttackStrengthTicker();

    @Shadow
    protected abstract boolean wantsToStopRiding();

    /**
     * Damages gloves only once during a sweeping attack, instead of once for every damaged entity in the attack.
     *
     * @param target The target {@link Entity}.
     * @param ci     The {@link CallbackInfo} for the void method return.
     * @see AbilityHooks.AccessoryHooks#damageGloves(Player)
     */
    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setLastHurtMob(Lnet/minecraft/world/entity/Entity;)V", shift = At.Shift.AFTER), method = "attack(Lnet/minecraft/world/entity/Entity;)V")
    private void attack(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (target instanceof LivingEntity) {
            AbilityHooks.AccessoryHooks.damageGloves(player);
        }
    }

    /**
     * Used to set whether the player tried to crouch for {@link MountableAnimal}, before crouching is cancelled for mounts by the {@link Player} class.
     *
     * @param ci The {@link CallbackInfo} for the void method return.
     */
    @Inject(at = @At(value = "HEAD"), method = "rideTick()V")
    private void rideTickHead(CallbackInfo ci, @Share("wantsToStopRiding") LocalBooleanRef wantsToStopRiding) {
        Player player = (Player) (Object) this;
        wantsToStopRiding.set(this.wantsToStopRiding());
        if (!player.level().isClientSide()) {
            if (player.isPassenger() && player.getVehicle() instanceof MountableAnimal mountableAnimal) {
                mountableAnimal.setPlayerTriedToCrouch(player.isShiftKeyDown());
            }
        }
    }

    @Inject(at = @At(value = "TAIL"), method = "rideTick()V")
    private void rideTickTail(CallbackInfo ci, @Share("wantsToStopRiding") LocalBooleanRef wantsToStopRiding) {
        Player player = (Player) (Object) this;
        if (!player.level().isClientSide() && !player.isShiftKeyDown() && wantsToStopRiding.get()) {
            if (player.isPassenger() && player.getVehicle() instanceof MountableAnimal) {
                player.setShiftKeyDown(true);
            }
        }
    }

    /**
     * Sets the player as having a loaded cape if they have a cape accessory equipped and visible.
     *
     * @param cir The {@link Boolean} {@link CallbackInfoReturnable} used for the method's return value.
     */
    @Inject(at = @At(value = "HEAD"), method = "isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z", cancellable = true)
    private void isModelPartShown(PlayerModelPart part, CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        ItemStack stack = AetherMixinHooks.isCapeVisible(player);
        if (!stack.isEmpty()) {
            cir.setReturnValue(true);
        }
    }

    //--

    @WrapMethod(method = "tick")
    private void aetherFabric$playerTickEvents(Operation<Void> original) {
        PlayerTickEvents.BEFORE.invoker().beforeTick((Player) (Object) this);
        original.call();
        PlayerTickEvents.AFTER.invoker().afterTick((Player) (Object) this);
    }

    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    private float aetherFabric$modifySpeed(float value, @Local(argsOnly = true) BlockState state) {
        var speed = new MutableFloat(value);
        var callback = new CancellableCallbackImpl();

        PlayerEvents.ON_BLOCK_DESTROY.invoker().onDestroy((Player) (Object) this, state, speed, callback);

        return callback.isCanceled() ? -1 : speed.getValue();
    }

    @WrapOperation(method = "attack", constant = @Constant(classValue = SwordItem.class))
    private boolean aetherFabric$preventSweeping(Object object, Operation<Boolean> original) {
        return original.call(object) && !(object instanceof ValkyrieLanceItem);
    }

    @WrapOperation(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;resetAttackStrengthTicker()V"))
    private void aetherFabric$preventResetHere(Player instance, Operation<Void> original) {
        // NO-OP
    }

    @Inject(method = "attack", at = @At(value = "RETURN", ordinal = 3))
    private void aetherFabric$callResetAtEnd(Entity target, CallbackInfo ci) {
        this.resetAttackStrengthTicker();
    }

    @WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float aetherFabric$adjustDamageAmount(Player instance, DamageSource damageSource, float damageAmount, Operation<Float> original) {
        var newDamage = new MutableFloat(original.call(instance, damageSource, damageAmount));

        LivingEntityEvents.ON_DAMAGE.invoker().modifyDamage((LivingEntity) (Object) this, damageSource, damageAmount, newDamage);

        return newDamage.getValue();
    }
}
