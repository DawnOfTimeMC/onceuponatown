package com.dotteam.onceuponatown.network;

import com.dotteam.onceuponatown.menu.SellMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class C2SSellScreenPacket {
    private final int dealIndex;
    private final RequestType requestType;

    public C2SSellScreenPacket(int dealIndex, RequestType requestType) {
        this.dealIndex = dealIndex;
        this.requestType = requestType;
    }

    public C2SSellScreenPacket(FriendlyByteBuf buffer) {
        this.dealIndex = buffer.readVarInt();
        this.requestType = buffer.readEnum(RequestType.class);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.dealIndex);
        buffer.writeEnum(this.requestType);
    }

    public int getDealIndex() {
        return this.dealIndex;
    }

    public RequestType getRequestType() {
        return this.requestType;
    }

    public boolean handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            int dealIndex = this.getDealIndex();
            AbstractContainerMenu containerMenu = context.getSender().containerMenu;
            if (containerMenu instanceof SellMenu sellMenu) {
                if (!sellMenu.stillValid( context.getSender())) {
                    Logger LOGGER = LogUtils.getLogger();
                    LOGGER.debug("Player {} interacted with invalid menu {}", context.getSender(), sellMenu);
                }
                else {
                    sellMenu.setSelectedDeal(dealIndex);
                    sellMenu.handleClientAction(dealIndex, this.requestType);
                }
            }
        });
        return true;
    }

    public enum RequestType {
        ONE_TRADE_SELL_ONE,
        ONE_TRADE_REMOVE_ONE,
        ONE_TRADE_SELL_EVERYTHING,
        ONE_TRADE_REMOVE_EVERYTHING,
        ALL_TRADES_SELL_EVERYTHING
    }
}