package com.wxwr.kaleidoscopeagricultureevolution.network;

import com.wxwr.kaleidoscopeagricultureevolution.item.WhipItem;
import com.wxwr.kaleidoscopeagricultureevolution.region.RegionMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务器：玩家潜行并手持鞭子时滚动了鼠标滚轮。
 *
 * <p>通知服务器将鞭子的当前 {@link RegionMode}
 * 向前或向后循环一步。
 */
public record ModeScrollPacket(boolean forward) {

    // ---- 编码 -----------------------------------------------------------

    public static void encode(ModeScrollPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.forward);
    }

    public static ModeScrollPacket decode(FriendlyByteBuf buf) {
        return new ModeScrollPacket(buf.readBoolean());
    }

    // ---- 处理 -----------------------------------------------------------

    public static void handle(ModeScrollPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Player player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof WhipItem whip)) return;

            whip.cycleMenuSelection(stack, msg.forward);
        });
        ctx.get().setPacketHandled(true);
    }
}
