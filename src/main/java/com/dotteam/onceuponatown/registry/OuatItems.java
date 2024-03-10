package com.dotteam.onceuponatown.registry;

import com.dotteam.onceuponatown.OuatConstants;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class OuatItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, OuatConstants.MOD_ID);

    public static final RegistryObject<Item> CITIZEN_SPAWN_EGG = ITEMS.register("citizen_spawn_egg", () ->
            new ForgeSpawnEggItem(OuatEntities.CITIZEN, 0x96691f, 0x38b934/*51A03E*/, new Item.Properties()));

    public static final RegistryObject<Item> EMERALD_SHARD = ITEMS.register("emerald_shard", () ->
            new Item(new Item.Properties()));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}