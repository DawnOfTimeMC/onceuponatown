package com.dotteam.onceuponatown.event;

import com.dotteam.onceuponatown.OuatConstants;
import com.dotteam.onceuponatown.entity.Citizen;
import com.dotteam.onceuponatown.registry.OuatCommands;
import com.dotteam.onceuponatown.town.TownManager;
import com.dotteam.onceuponatown.util.OuatLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OuatConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEvents {
    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        //event.addListener(CultureManager.instance());
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.level instanceof ServerLevel level && event.phase.equals(TickEvent.Phase.END)) {
            TownManager.tickTowns(level);
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        OuatCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity() instanceof Citizen citizen) {
            citizen.onFinalizeSpawnEvent();
        }
    }
}
