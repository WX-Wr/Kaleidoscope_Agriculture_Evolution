package com.wxwr.kaleidoscopeagricultureevolution.network;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.client.ClientCropVisualSyncHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CropVisualSyncPacket(BlockPos pos, boolean present, boolean highYieldCrop,
                                   char yieldGrade) {

    public static void encode(CropVisualSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeBoolean(msg.present);
        buf.writeBoolean(msg.highYieldCrop);
        buf.writeChar(msg.yieldGrade);
    }

    public static CropVisualSyncPacket decode(FriendlyByteBuf buf) {
        return new CropVisualSyncPacket(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(),
            buf.readChar());
    }

    public static void handle(CropVisualSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCropVisualSyncHandler.handle(msg)));
        ctx.get().setPacketHandled(true);
    }
}
