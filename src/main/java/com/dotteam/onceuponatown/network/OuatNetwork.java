package com.dotteam.onceuponatown.network;

import com.dotteam.onceuponatown.OuatConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class OuatNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE =  NetworkRegistry.newSimpleChannel(new ResourceLocation(OuatConstants.MOD_ID, "channel"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
    private static int packetId = 0;

    private OuatNetwork() {}

    private static int id() {
        return packetId++;
    }

    public static void init() {
        INSTANCE.registerMessage(id(),
                C2SSelectBuyDealPacket.class,
                C2SSelectBuyDealPacket::write,
                C2SSelectBuyDealPacket::new,
                C2SSelectBuyDealPacket::handle);

        INSTANCE.registerMessage(id(),
                C2SSellScreenPacket.class,
                C2SSellScreenPacket::write,
                C2SSellScreenPacket::new,
                C2SSellScreenPacket::handle);

        INSTANCE.registerMessage(id(),
                C2SChangeCitizenTabPacket.class,
                C2SChangeCitizenTabPacket::write,
                C2SChangeCitizenTabPacket::new,
                C2SChangeCitizenTabPacket::handle);
    }

    public static <MSG> void sendToServer(MSG pMessage) {
        INSTANCE.sendToServer(pMessage);
    }

    public static <MSG> void sendToPlayer(MSG pMessage, ServerPlayer pPlayer) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> pPlayer), pMessage);
    }

    public static <MSG> void sendToAllPlayers(MSG pMessage) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), pMessage);
    }
}
