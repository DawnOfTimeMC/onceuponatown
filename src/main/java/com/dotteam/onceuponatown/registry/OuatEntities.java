package com.dotteam.onceuponatown.registry;

import com.dotteam.onceuponatown.OuatConstants;
import com.dotteam.onceuponatown.entity.Citizen;
import com.dotteam.onceuponatown.util.OuatUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class OuatEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, OuatConstants.MOD_ID);

    public static final RegistryObject<EntityType<Citizen>> CITIZEN = ENTITY_TYPES.register("citizen", () -> EntityType.Builder
            .of(Citizen::new, MobCategory.MISC)
            .sized(0.6F, 1.95F)
            .clientTrackingRange(10)
            .build(OuatUtils.resource("citizen").toString())
    );

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}