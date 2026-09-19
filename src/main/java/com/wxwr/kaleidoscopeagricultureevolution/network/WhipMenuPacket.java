package com.wxwr.kaleidoscopeagricultureevolution.network;

import com.wxwr.kaleidoscopeagricultureevolution.item.WhipItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record WhipMenuPacket() {
    public static void encode(WhipMenuPacket msg, FriendlyByteBuf buf) {
    }

    public static WhipMenuPacket decode(FriendlyByteBuf buf) {
        return new WhipMenuPacket();
    }

    public static void handle(WhipMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Player player = ctx.get().getSender();
            if (player == null || !player.isShiftKeyDown()) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof WhipItem whip)) return;

            whip.advanceMenuLayer(stack);
        });
        ctx.get().setPacketHandled(true);
    }
}
