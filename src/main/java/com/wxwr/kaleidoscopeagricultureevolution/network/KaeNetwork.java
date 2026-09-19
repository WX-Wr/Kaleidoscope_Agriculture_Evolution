package com.wxwr.kaleidoscopeagricultureevolution.network;

import com.wxwr.kaleidoscopeagricultureevolution.KaleidoscopeAgricultureEvolution;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 所有自定义网络消息的中央注册中心。
 *
 * <p>当数据包格式发生不兼容更改时，
 * 协议版本号需要递增。
 */
public final class KaeNetwork {

    private static final String PROTOCOL_VERSION = "5";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            KaleidoscopeAgricultureEvolution.rl("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId;

    private KaeNetwork() {}

    /** 在模组构建/公共初始化期间调用一次。 */
    public static void register() {
        CHANNEL.messageBuilder(ModeScrollPacket.class, nextId())
                .encoder(ModeScrollPacket::encode)
                .decoder(ModeScrollPacket::decode)
                .consumerNetworkThread(ModeScrollPacket::handle)
                .add();
        CHANNEL.messageBuilder(WhipMenuPacket.class, nextId())
                .encoder(WhipMenuPacket::encode)
                .decoder(WhipMenuPacket::decode)
                .consumerNetworkThread(WhipMenuPacket::handle)
                .add();
        CHANNEL.messageBuilder(CropVisualSyncPacket.class, nextId())
                .encoder(CropVisualSyncPacket::encode)
                .decoder(CropVisualSyncPacket::decode)
                .consumerNetworkThread(CropVisualSyncPacket::handle)
                .add();
        CHANNEL.messageBuilder(HoneyExtractorScrollPacket.class, nextId())
                .encoder(HoneyExtractorScrollPacket::encode)
                .decoder(HoneyExtractorScrollPacket::decode)
                .consumerNetworkThread(HoneyExtractorScrollPacket::handle)
                .add();
        CHANNEL.messageBuilder(HoneyExtractorStartPacket.class, nextId())
                .encoder(HoneyExtractorStartPacket::encode)
                .decoder(HoneyExtractorStartPacket::decode)
                .consumerNetworkThread(HoneyExtractorStartPacket::handle)
                .add();
        CHANNEL.messageBuilder(HoneyExtractorStopPacket.class, nextId())
                .encoder(HoneyExtractorStopPacket::encode)
                .decoder(HoneyExtractorStopPacket::decode)
                .consumerNetworkThread(HoneyExtractorStopPacket::handle)
                .add();
    }

    private static int nextId() {
        return packetId++;
    }
}
