package dev.autobridge.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 切换开关时在快捷栏上方闪一条提示。
 *
 * <p>只在切换后的两秒内出现，平时不占屏幕 —— 搭路是常驻行为，没必要一直提示。</p>
 */
public class AutoBridgeHud implements HudElement {

    private static final int BG = 0x90000000;
    private static final int PAD = 4;
    private static final int COLOR = 0xFFFFFFFF;
    private static final int COLOR_OFF = 0xFFAAAAAA;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g) {
        if (!AutoBridgeClient.hudVisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return;
        }
        // F1 隐藏界面时跟着藏
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden()) {
            return;
        }
        String text = AutoBridgeClient.hudText();
        int width = mc.font.width(text);
        int x = (g.guiWidth() - width) / 2;
        int y = g.guiHeight() - 62;
        g.fill(x - PAD, y - PAD, x + width + PAD, y + 9 + PAD, BG);
        g.text(mc.font, text, x, y, text.contains("已开启") || text.endsWith("ON") ? COLOR : COLOR_OFF);
    }
}
