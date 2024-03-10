package com.dotteam.onceuponatown.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class CitizenBaseMenu extends AbstractContainerMenu {
    protected InteractableCitizen citizen;

    protected CitizenBaseMenu(@Nullable MenuType<?> menuType, int containerId, InteractableCitizen citizen) {
        super(menuType, containerId);
        this.citizen = citizen;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.citizen.getInteractingPlayer() == player;
    }

    public InteractableCitizen getCitizen() {
        return citizen;
    }
}
