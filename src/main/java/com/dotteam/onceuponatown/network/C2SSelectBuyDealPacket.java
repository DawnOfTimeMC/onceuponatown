package com.dotteam.onceuponatown.network;

import com.dotteam.onceuponatown.menu.BuyMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class C2SSelectBuyDealPacket {
    private final int dealIndex;

    public C2SSelectBuyDealPacket(int dealIndex) {
        this.dealIndex = dealIndex;
    }

    public C2SSelectBuyDealPacket(FriendlyByteBuf buffer) {
        this.dealIndex = buffer.readVarInt();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.dealIndex);
    }

    public int getDealIndex() {
        return this.dealIndex;
    }

    public boolean handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            int dealIndex = this.getDealIndex();
            AbstractContainerMenu containerMenu = context.getSender().containerMenu;
            if (containerMenu instanceof BuyMenu buyMenu) {
                if (!buyMenu.stillValid(context.getSender())) {
                    Logger LOGGER = LogUtils.getLogger();
                    LOGGER.debug("Player {} interacted with invalid menu {}", context.getSender(), buyMenu);
                } else {
                    buyMenu.setSelectedDeal(dealIndex);
                    buyMenu.tryMoveItems(dealIndex);
                }
            }
        });
        return true;
    }
}