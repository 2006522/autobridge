package dev.autobridge;

import dev.autobridge.net.BridgeUsePayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * 通用入口（客户端与服务端都会加载）：读配置、注册心跳包、每刻兜底铺路。
 *
 * <p>真正的放置逻辑在服务端（{@link BridgeBuilder}），所以<b>逻辑上只需要装在服务端</b>；
 * 但"右键是否按住"要靠客户端发心跳，所以要自动搭路就必须两端都装（单人存档天然都有）。</p>
 */
public class AutoBridge implements ModInitializer {

    @Override
    public void onInitialize() {
        AutoBridgeConfig.loadIfNeeded();

        PayloadTypeRegistry.serverboundPlay().register(BridgeUsePayload.TYPE, BridgeUsePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BridgeUsePayload.TYPE, (payload, context) ->
                // 网络线程不能碰世界状态，丢回主线程
                context.server().execute(() -> BridgeBuilder.onSignal(context.player())));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                BridgeBuilder.tick(player);
            }
        });

        Log.info("已加载：按住右键往前走，会自动在脚前铺方块（B 键开关）");
    }
}
