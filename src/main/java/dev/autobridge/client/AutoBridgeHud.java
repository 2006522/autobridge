package dev.autobridge.client;

import dev.autobridge.AutoBridgeConfig;
import dev.autobridge.BridgeProbe;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 两个提示：
 *
 * <ol>
 *   <li><b>准星下方的倒三角</b>（常驻）：亮青 = 现在按住右键立刻会铺；淡灰 = 暂时不会铺
 *       （没在走 / 前面有地面 / 手头没有能当砖的方块）。</li>
 *   <li><b>切换开关时的一条文字</b>：只在切换后两秒内出现。</li>
 * </ol>
 *
 * <p>三角的判定走 {@link BridgeProbe}，和服务端真正铺路用的是同一份条件 ——
 * 亮着就一定铺得出来，不会出现"提示说能铺、按下去没反应"。</p>
 */
public class AutoBridgeHud implements HudElement {

    private static final int BG = 0x90000000;
    private static final int PAD = 4;
    private static final int COLOR = 0xFFFFFFFF;
    private static final int COLOR_OFF = 0xFFAAAAAA;

    /** 倒三角：底边 9 px、高 5 px，顶边离准星中心 9 px。 */
    private static final int TRI_W = 9;
    private static final int TRI_H = 5;
    private static final int TRI_GAP = 9;

    /** 就绪时用配置里的颜色（默认青蓝）。 */
    private static final int TRI_DEFAULT = 0x5CDBFF;
    /** 暂时不会铺时的淡灰白。 */
    private static final int TRI_IDLE = 0x66FFFFFF;
    /** 垫在三角下面的阴影，亮背景（天空）上也能看清。 */
    private static final int TRI_SHADOW = 0x60000000;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        // F1 隐藏界面时跟着藏
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden()) {
            return;
        }
        // 开着任意界面时准星本身都不画，三角也别留着
        if (mc.gui != null && mc.gui.screen() != null) {
            return;
        }
        if (mc.player.isSpectator()) {
            return;
        }

        if (AutoBridgeConfig.crosshairHint) {
            drawCrosshairTriangle(g, mc);
        }
        drawToggleToast(g, mc);
    }

    // ---------------------------------------------------------------- 准星倒三角

    private void drawCrosshairTriangle(GuiGraphicsExtractor g, Minecraft mc) {
        boolean ready = BridgeProbe.ready(mc.player);
        int cx = g.guiWidth() / 2;
        int top = g.guiHeight() / 2 + TRI_GAP;

        // 影子往下偏 1 px、左右各宽 1 px，勾一圈轮廓
        triangle(g, cx, top + 1, TRI_W + 2, TRI_H, TRI_SHADOW);

        if (ready) {
            // 呼吸：900 ms 一个来回，亮度 180 → 255，看得出"活着"但不刺眼
            double phase = (Math.sin(System.currentTimeMillis() / 900.0D * Math.PI * 2.0D) + 1.0D) / 2.0D;
            int alpha = 0xB4 + (int) Math.round(0x4B * phase);
            triangle(g, cx, top, TRI_W, TRI_H, (alpha << 24) | (AutoBridgeConfig.hintColor & 0xFFFFFF));
        } else {
            triangle(g, cx, top, TRI_W, TRI_H, TRI_IDLE);
        }
    }

    /**
     * 画一个尖朝下的实心三角。
     *
     * <p>逐行 {@code fill} 拼出来，最上一行最宽、最下一行 1 px —— 不依赖任何贴图，
     * 换 GUI 缩放也不糊。</p>
     */
    private static void triangle(GuiGraphicsExtractor g, int cx, int top, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            int rw = Math.max(1, w - (int) Math.round((double) (w - 1) * row / (double) (h - 1)));
            int x = cx - rw / 2;
            g.fill(x, top + row, x + rw, top + row + 1, color);
        }
    }

    // ---------------------------------------------------------------- 切换提示

    private void drawToggleToast(GuiGraphicsExtractor g, Minecraft mc) {
        if (!AutoBridgeClient.hudVisible() || mc.font == null) {
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
