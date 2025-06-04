package com.aetherteam.aether.mixin.mixins.common;

import com.aetherteam.aether.AetherConfig;
import com.aetherteam.aether.AetherTags;
import com.aetherteam.aether.attachment.AetherDataAttachments;
import com.aetherteam.aether.event.hooks.DimensionHooks;
import com.aetherteam.aether.item.AetherItems;
import com.aetherteam.aether.item.EquipmentUtil;
import com.aetherteam.aether.item.combat.abilities.armor.PhoenixArmor;
import com.aetherteam.aether.item.combat.abilities.weapon.SkyrootWeapon;
import com.aetherteam.aether.network.packet.clientbound.SetVehiclePacket;
import com.aetherteam.aether.world.LevelUtil;
import com.aetherteam.aetherfabric.entity.IEntityWithComplexSpawn;
import com.aetherteam.aetherfabric.events.CancellableCallbackImpl;
import com.aetherteam.aetherfabric.events.EntityEvents;
import com.aetherteam.aetherfabric.events.EntityTickEvents;
import com.aetherteam.aetherfabric.network.PacketDistributor;
import com.aetherteam.aetherfabric.network.payload.AdvancedAddEntityPayload;
import com.aetherteam.aetherfabric.pond.EntityExtension;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Consumer;

@Mixin(Entity.class)
public class EntityMixin implements EntityExtension {
    @Shadow
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight;

    @Shadow
    private @Nullable Entity vehicle;

    /**
     * Handles entities falling out of the Aether. If an entity is not a player, vehicle, or tracked item, it is removed.
     *
     * @param ci The {@link CallbackInfo} for the void method return.
     * @see PhoenixArmor#boostVerticalLavaSwimming(LivingEntity)
     */
    @Inject(at = @At(value = "TAIL"), method = "tick()V")
    private void travel(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        Level level = entity.level();
        if (level instanceof ServerLevel serverLevel) {
            if (!AetherConfig.SERVER.disable_falling_to_overworld.get()) {
                if (serverLevel.dimension() == LevelUtil.destinationDimension()) {
                    if (entity.getY() <= serverLevel.getMinBuildHeight() && !entity.isPassenger()) {
                        if (entity instanceof Player || entity.isVehicle() || (entity instanceof Saddleable) && ((Saddleable) entity).isSaddled()) { // Checks if an entity is a player or a vehicle of a player.
                            entityFell(entity);
                        } else if (entity instanceof Projectile projectile && projectile.getOwner() instanceof Player) {
                            entityFell(projectile);
                        } else if (entity instanceof ItemEntity itemEntity) {
                            if (itemEntity.hasAttached(AetherDataAttachments.DROPPED_ITEM)) {
                                if (itemEntity.getOwner() instanceof Player || itemEntity.getAttached(AetherDataAttachments.DROPPED_ITEM).getOwner(level) instanceof Player) { // Checks if an entity is an item that was dropped by a player.
                                    entityFell(entity);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Code to handle falling out of the Aether with all passengers intact.
     *
     * @param entity The {@link Entity}
     */
    @Unique
    @Nullable
    private static Entity entityFell(Entity entity) {
        Level serverLevel = entity.level();
        MinecraftServer minecraftserver = serverLevel.getServer();
        if (entity instanceof AbstractArrow) {
            return null;
        }
        if (minecraftserver != null) {
            ServerLevel destination = minecraftserver.getLevel(LevelUtil.returnDimension());
            if (destination != null && LevelUtil.returnDimension() != LevelUtil.destinationDimension()) {
                List<Entity> passengers = entity.getPassengers();
                serverLevel.getProfiler().push("aether_fall");
                entity.setPortalCooldown();
                double vehicleOffset = 0.0;
                if (entity.getVehicle() != null) {
                    vehicleOffset = entity.getVehicle().getBbHeight();
                }
                DimensionTransition transition = new DimensionTransition(destination, new Vec3(entity.getX(), destination.getMaxBuildHeight() - entity.getBbHeight() - vehicleOffset, entity.getZ()), entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), false, DimensionTransition.DO_NOTHING);
                Entity target = entity.changeDimension(transition);
                serverLevel.getProfiler().pop();
                // Check for passengers.
                if (target != null) {
                    for (Entity passenger : passengers) {
                        passenger.stopRiding();
                        Entity nextPassenger = entityFell(passenger);
                        if (nextPassenger != null) {
                            nextPassenger.startRiding(target);
                            if (target instanceof ServerPlayer serverPlayer) { // Fixes a desync between the server and client.
                                PacketDistributor.sendToPlayer(serverPlayer, new SetVehiclePacket(nextPassenger.getId(), target.getId()));
                            }
                        }
                    }
                    if (target instanceof ServerPlayer) {
                        DimensionHooks.teleportationTimer = 500; // Sets a timer marking that the player teleported from falling out of the Aether.
                    }
                }
                return target;
            }
        }
        return null;
    }

    //--

    @WrapOperation(method = "rideTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void aetherFabric$entityTickEvents(Entity instance, Operation<Void> original) {
        var shouldCancelEvent = new MutableBoolean(false);

        EntityTickEvents.BEFORE.invoker().beforeTick(instance, shouldCancelEvent);

        if (!shouldCancelEvent.getValue()) {
            original.call(instance);
            EntityTickEvents.AFTER.invoker().afterTick(instance);
        }
    }

    @Inject(method = "changeDimension", at = @At("HEAD"))
    private void aetherFabric$beforeDimensionChange(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        EntityEvents.BEFORE_DIMENSION_CHANGE.invoker().beforeChange((Entity) (Object) this, transition.newLevel().dimension());
    }

    @Override
    public boolean aetherFabric$isInFluidType() {
        for (var value : this.fluidHeight.values()) {
            if (value > 0.0) return true;
        }

        return false;
    }

    @Definition(id = "vehicle", field = "Lnet/minecraft/world/entity/Entity;vehicle:Lnet/minecraft/world/entity/Entity;")
    @Expression("this.vehicle = null")
    @Inject(method = "removeVehicle", at = @At(value = "MIXINEXTRAS:EXPRESSION", shift = At.Shift.BEFORE), cancellable = true)
    private void aetherFabric$entityMountEvent_remove(CallbackInfo ci) {
        var callback = new CancellableCallbackImpl();

        var entityMounting = (Entity)(Object) this;

        EntityEvents.ENTITY_MOUNT.invoker().onMount(entityMounting, this.vehicle, true, callback);

        if (callback.isCanceled()) {
            entityMounting.absMoveTo(entityMounting.getX(), entityMounting.getY(), entityMounting.getZ(), entityMounting.yRotO, entityMounting.xRotO);
            ci.cancel();
        }
    }

    @Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;canRide(Lnet/minecraft/world/entity/Entity;)Z"), cancellable = true)
    private void aetherFabric$entityMountEvent_add(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        var callback = new CancellableCallbackImpl();

        var entityMounting = (Entity)(Object) this;

        EntityEvents.ENTITY_MOUNT.invoker().onMount(entityMounting, this.vehicle, false, callback);

        if (callback.isCanceled()) {
            entityMounting.absMoveTo(entityMounting.getX(), entityMounting.getY(), entityMounting.getZ(), entityMounting.yRotO, entityMounting.xRotO);
            cir.setReturnValue(false);
        }
    }

    @Override
    public void aetherFabric$sendPairingData(ServerPlayer serverPlayer, Consumer<CustomPacketPayload> bundleBuilder) {
        if (this instanceof IEntityWithComplexSpawn) {
            bundleBuilder.accept(new AdvancedAddEntityPayload((Entity)(Object) this));
        }
    }

    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private void aetherFabric$captureDroppedStack(ItemStack stack, float offsetY, CallbackInfoReturnable<ItemEntity> cir, @Local() ItemEntity itemEntity) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player player) {
            itemEntity.getAttachedOrCreate(AetherDataAttachments.DROPPED_ITEM).setOwner(player);
        }
    }
}
