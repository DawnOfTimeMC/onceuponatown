package com.dotteam.onceuponatown.registry;

import com.dotteam.onceuponatown.OuatConstants;
import com.dotteam.onceuponatown.menu.BuyMenu;
import com.dotteam.onceuponatown.menu.SellMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class OuatMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, OuatConstants.MOD_ID);

    public static final RegistryObject<MenuType<BuyMenu>> BUY_MENU = MENUS.register("buy_menu",
            () -> IForgeMenuType.create(BuyMenu::new));

    public static final RegistryObject<MenuType<SellMenu>> SELL_MENU = MENUS.register("sell_menu",
            () -> IForgeMenuType.create(SellMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}