package com.wxwr.kaleidoscopeagricultureevolution.network;

import com.wxwr.kaleidoscopeagricultureevolution.blockentity.HoneyExtractorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HoneyExtractorStopPacket(BlockPos pos) {
    public static void encode(HoneyExtractorStopPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static HoneyExtractorStopPacket decode(FriendlyByteBuf buf) {
        return new HoneyExtractorStopPacket(buf.readBlockPos());
    }

    public static void handle(HoneyExtractorStopPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Player player = ctx.get().getSender();
            if (player == null || player.distanceToSqr(msg.pos.getX() + 0.5D,
                    msg.pos.getY() + 0.5D, msg.pos.getZ() + 0.5D) > 64.0D) {
                return;
            }
            if (player.level().getBlockEntity(msg.pos) instanceof HoneyExtractorBlockEntity extractor) {
                extractor.endInteraction(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
