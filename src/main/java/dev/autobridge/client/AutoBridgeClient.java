package dev.autobridge.client;

import dev.autobridge.AutoBridgeConfig;
import dev.autobridge.net.BridgeUsePayload;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * 客户端入口：发"按住右键"的心跳、注册 B 键开关和提示。
 *
 * <p>26.x 的输入走 SDL，KeyMapping 要的是 SDL 扫描码（这里用 {@code KEY_*} 常量，
 * 不是 GLFW 的数字）。</p>
 */
public class AutoBridgeClient implements ClientModInitializer {

    public static KeyMapping toggleKey;
    public static KeyMapping configKey;

    /** HUD 提示：切换开关时显示两秒。 */
    private static long hudUntil;
    private static String hudText = "";

    @Override
    public void onInitializeClient() {
        AutoBridgeConfig.loadIfNeeded();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.parse("autobridge"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autobridge.toggle", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, category));
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.autobridge.config", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, category));

        HudElementRegistry.addLast(Identifier.parse("autobridge:hud"), new AutoBridgeHud());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey != null && toggleKey.consumeClick()) {
                AutoBridgeConfig.enable = !AutoBridgeConfig.enable;
                AutoBridgeConfig.save();
                show(AutoBridgeConfig.enable ? "自动搭路 已开启" : "自动搭路 已关闭");
            }
            while (configKey != null && configKey.consumeClick()) {
                if (client.gui != null && client.gui.screen() == null) {
                    client.setScreenAndShow(new ConfigScreen(null));
                }
            }
        });
    }

    /**
     * 通知服务端"我正在按右键"。由 {@code Minecraft#startUseItem} 的 mixin 调用，
     * 每 4 刻一次（正好是原版右键的节流周期）。
     */
    public static void sendUseSignal() {
        if (!AutoBridgeConfig.enable) {
            return;
        }
        try {
            // 纯原版服务端没有这个通道，不检查会一直抛异常刷日志
            if (ClientPlayNetworking.canSend(BridgeUsePayload.TYPE)) {
                ClientPlayNetworking.send(new BridgeUsePayload());
            }
        } catch (Exception ignored) {
            // 通道没注册好（比如刚断线）就算了，下一拍再发
        }
    }

    static void show(String text) {
        hudText = text;
        hudUntil = System.currentTimeMillis() + 2000L;
    }

    static boolean hudVisible() {
        return System.currentTimeMillis() < hudUntil;
    }

    static String hudText() {
        return hudText;
    }
}
