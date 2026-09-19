package com.wxwr.kaleidoscopeagricultureevolution.network;

import com.wxwr.kaleidoscopeagricultureevolution.client.HoneyExtractorClientInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HoneyExtractorStartPacket(BlockPos pos) {
    public static void encode(HoneyExtractorStartPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static HoneyExtractorStartPacket decode(FriendlyByteBuf buf) {
        return new HoneyExtractorStartPacket(buf.readBlockPos());
    }

    public static void handle(HoneyExtractorStartPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> HoneyExtractorClientInteraction.start(msg.pos)));
        ctx.get().setPacketHandled(true);
    }
}
