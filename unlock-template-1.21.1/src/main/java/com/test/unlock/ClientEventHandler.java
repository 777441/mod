package com.test.unlock;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

public class ClientEventHandler {
    private static boolean hasUnlockedDex = false;
    private static int tickCounter = 0;

    public static void init() {
        // 在客户端连接到服务器时
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            hasUnlockedDex = false;
            tickCounter = 0;
            UnlockDexMod.LOGGER.info("玩家加入服务器，重置解锁状态");
        });

        // 在客户端tick时检查是否需要解锁图鉴
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!hasUnlockedDex && client.player != null) {
                tickCounter++;
                // 等待一段时间再解锁，确保客户端数据已加载
                if (tickCounter > 200) {  // 约10秒
                    UnlockDexMod.LOGGER.info("开始自动解锁图鉴");
                    UnlockDexMod.unlockAllPokemonClientSide();
                    hasUnlockedDex = true;
                    client.player.sendMessage(net.minecraft.text.Text.literal("§a客户端图鉴已自动解锁!"), true);
                }
            }
        });
    }
}