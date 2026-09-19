package com.wxwr.kaleidoscopeagricultureevolution.network;

import com.wxwr.kaleidoscopeagricultureevolution.blockentity.HoneyExtractorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务器：玩家看向摇蜜器并滚动了鼠标滚轮。
 */
public record HoneyExtractorScrollPacket(BlockPos pos, double scrollDelta, double deltaSeconds) {

    public static void encode(HoneyExtractorScrollPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeDouble(msg.scrollDelta);
        buf.writeDouble(msg.deltaSeconds);
    }

    public static HoneyExtractorScrollPacket decode(FriendlyByteBuf buf) {
        return new HoneyExtractorScrollPacket(buf.readBlockPos(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(HoneyExtractorScrollPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Player player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (player.distanceToSqr(msg.pos.getX() + 0.5D, msg.pos.getY() + 0.5D, msg.pos.getZ() + 0.5D) > 64.0D) {
                return;
            }
            if (player.level().getBlockEntity(msg.pos) instanceof HoneyExtractorBlockEntity extractor) {
                if (extractor.isInteracting(player)) {
                    extractor.applyWheelInput(msg.scrollDelta, msg.deltaSeconds);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
