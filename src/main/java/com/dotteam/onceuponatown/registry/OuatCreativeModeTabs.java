package com.dotteam.onceuponatown.registry;

import com.dotteam.onceuponatown.OuatConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class OuatCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OuatConstants.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN_CREATIVE_TAB = CREATIVE_MODE_TABS.register("main_tab", () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(Items.EMERALD))
            .title(Component.translatable("creative_mode_tab.onceuponatown.main_tab"))
            .displayItems((parameters, output) -> {
                output.accept(OuatItems.CITIZEN_SPAWN_EGG.get());
                output.accept(OuatItems.EMERALD_SHARD.get());
            }).build());

    public static void register(IEventBus bus) {
        CREATIVE_MODE_TABS.register(bus);
    }
}
