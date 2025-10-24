package com.aetherteam.aether.data;

import com.aetherteam.aether.Aether;
import com.aetherteam.aether.block.FreezingBlock;
import com.aetherteam.aether.item.AetherItems;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;


public class ReloadListeners {
    /**
     * @see Aether#eventSetup()
     */
    public static void reloadListenerSetup(ResourceManagerHelper helper) {
        helper.registerReloadListener(new RecipeReloadListener());
        helper.registerReloadListener(new BannerReloadListener());
    }

    public static class RecipeReloadListener extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
        public static final Gson GSON_INSTANCE = new GsonBuilder().create();

        public RecipeReloadListener() {
            super(GSON_INSTANCE, Registries.elementsDirPath(Registries.RECIPE));
        }

        /**
         * Resets the block caches for {@link FreezingBlock} recipes.
         */
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
            FreezingBlock.cachedBlocks.clear();
            FreezingBlock.cachedResults.clear();
        }

        @Override
        public ResourceLocation getFabricId() {
            return ResourceLocation.fromNamespaceAndPath(Aether.MODID, Registries.elementsDirPath(Registries.RECIPE));
        }
    }

    public static class BannerReloadListener extends SimpleJsonResourceReloadListener implements IdentifiableResourceReloadListener {
        public static final Gson GSON_INSTANCE = new GsonBuilder().create();

        public BannerReloadListener() {
            super(GSON_INSTANCE, Registries.elementsDirPath(Registries.BANNER_PATTERN));
        }

        /**
         * Resets the block caches for {@link FreezingBlock} recipes.
         */
        @Override
        protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
            AetherItems.SWET_BANNER = null;
        }

        @Override
        public ResourceLocation getFabricId() {
            return ResourceLocation.fromNamespaceAndPath(Aether.MODID, Registries.elementsDirPath(Registries.BANNER_PATTERN));
        }
    }
}
