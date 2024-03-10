package com.dotteam.onceuponatown.util;

import com.dotteam.onceuponatown.OuatConstants;
import com.dotteam.onceuponatown.construction.EntityInfo;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class OuatUtils {
    public static ResourceLocation resource(String name) {
        return new ResourceLocation(OuatConstants.MOD_ID, name);
    }

    public static Vec3i getStructureDimensions(ResourceLocation path, ResourceManager resourceManager) {
        FileToIdConverter converter = new FileToIdConverter("structures", ".nbt");
        ResourceLocation resourceLocation = converter.idToFile(path);
        try (InputStream inputStream = resourceManager.open(resourceLocation)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            ListTag sizeTag = tag.getList("size", 3);
            return new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        } catch (FileNotFoundException fileNotFoundException) {
            LogUtils.getLogger().error("Structure not found {}", resourceLocation, fileNotFoundException);
            return null;
        } catch (Throwable throwable) {
            LogUtils.getLogger().error("Error loading structure {}", resourceLocation, throwable);
            return null;
        }
    }

    public static List<EntityInfo> getStructureEntities(ResourceLocation path, ResourceManager resourceManager) {
        FileToIdConverter converter = new FileToIdConverter("structures", ".nbt");
        ResourceLocation resourceLocation = converter.idToFile(path);
        try (InputStream inputStream = resourceManager.open(resourceLocation)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            ListTag entitiesTag = tag.getList("entities", 10);
            List<EntityInfo> entityInfos = new ArrayList<>();
            for(int i = 0; i < entitiesTag.size(); ++i) {
                CompoundTag entityTag = entitiesTag.getCompound(i);
                ListTag posTag = entityTag.getList("pos", 6);
                Vec3 pos = new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
                ListTag blockPosTag = entityTag.getList("blockPos", 3);
                BlockPos blockPos = new BlockPos(blockPosTag.getInt(0), blockPosTag.getInt(1), blockPosTag.getInt(2));
                if (entityTag.contains("nbt")) {
                    CompoundTag entityNBT = entityTag.getCompound("nbt");
                    entityInfos.add(new EntityInfo(pos, blockPos, entityNBT));
                }
            }
            return entityInfos;
        } catch (FileNotFoundException fileNotFoundException) {
            LogUtils.getLogger().error("Structure not found {}", resourceLocation, fileNotFoundException);
            return null;
        } catch (Throwable throwable) {
            LogUtils.getLogger().error("Error loading structure {}", resourceLocation, throwable);
            return null;
        }
    }
}
