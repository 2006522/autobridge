package dev.autobridge.client;

import dev.autobridge.AutoBridgeConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * 游戏内设置界面。
 *
 * <p>自绘的分组列表：每组一个小标题 + 若干行，右侧是开关或滑块。
 * 没依赖任何配置库 —— 26.4-snapshot-5 把 GUI 渲染换成了 {@code extract*} 系列，
 * 给旧快照编译的配置库（YACL 3.9.9 / 3.9.7）在这里覆写的方法签名对不上，
 * 界面会一片空白，所以自己画最稳。</p>
 *
 * <p>改任何一项都<b>即时生效并立刻写盘</b>，所以没有「保存」按钮，只有「完成」。</p>
 */
public class ConfigScreen extends Screen {

    private static final int PANEL_W = 330;
    private static final int ROW_H = 22;
    private static final int GROUP_H = 18;
    private static final int PAD = 8;
    private static final int TITLE_H = 24;
    private static final int FOOTER_H = 26;

    private static final int COL_PANEL = 0xF0181A20;
    private static final int COL_BORDER = 0xFF3A3F4B;
    private static final int COL_TITLE = 0xFFFFFFFF;
    private static final int COL_GROUP = 0xFF8FA4C0;
    private static final int COL_TEXT = 0xFFE0E4EA;
    private static final int COL_HINT = 0xFF8A90A0;
    private static final int COL_ROW_HOVER = 0x20FFFFFF;
    private static final int COL_ON = 0xFF35C46A;
    private static final int COL_OFF = 0xFF4A4F5C;
    private static final int COL_KNOB = 0xFFFFFFFF;
    private static final int COL_TRACK = 0xFF2A2E38;
    private static final int COL_TRACK_FILL = 0xFF35C46A;
    private static final int COL_BTN = 0xFF55596A;
    private static final int COL_BTN_OK = 0xFF2F6B47;

    private static final int TOGGLE_W = 30;
    private static final int TOGGLE_H = 12;
    private static final int SLIDER_MAX_W = 90;
    private static final int BTN_W = 74;
    private static final int BTN_H = 18;

    private final Screen parent;

    /** 按显示顺序混排「组标题」和「配置行」。 */
    private final List<Item> items = new ArrayList<>();

    private SliderRow dragging;
    private int panelX;
    private int panelY;
    private int panelH;

    public ConfigScreen(Screen parent) {
        super(Component.literal("自动搭路 设置"));
        this.parent = parent;
        buildItems();
    }

    // ---------------------------------------------------------------- 内容

    private void buildItems() {
        items.clear();

        items.add(new GroupHeader("搭路"));
        items.add(new ToggleRow("总开关",
                "关掉就完全按原版走（B 键同效）",
                () -> AutoBridgeConfig.enable, v -> AutoBridgeConfig.enable = v));
        items.add(new ToggleRow("只在移动时铺",
                "站着不动按右键还是正常放方块",
                () -> AutoBridgeConfig.needMoving, v -> AutoBridgeConfig.needMoving = v));
        items.add(new ToggleRow("仅潜行时铺",
                "更保守：必须蹲着走才铺路",
                () -> AutoBridgeConfig.onlyWhenSneaking, v -> AutoBridgeConfig.onlyWhenSneaking = v));
        items.add(new ToggleRow("只认完整方块",
                "不拿种子、树苗、火把、台阶当砖",
                () -> AutoBridgeConfig.requireFullBlock, v -> AutoBridgeConfig.requireFullBlock = v));
        items.add(new SliderRow("铺路间隔", 0, 20,
                () -> AutoBridgeConfig.placeDelay,
                v -> AutoBridgeConfig.placeDelay = (int) Math.round(v),
                v -> {
                    int ticks = (int) Math.round(v);
                    if (ticks <= 0) {
                        return "每刻都铺";
                    }
                    return ticks + " 刻 / " + String.format("%.1f", ticks / 20.0D) + " 秒";
                }));

        items.add(new GroupHeader("显示"));
        items.add(new ToggleRow("准星倒三角",
                "亮青=马上会铺，淡灰=暂时不会",
                () -> AutoBridgeConfig.crosshairHint, v -> AutoBridgeConfig.crosshairHint = v));
        items.add(new ToggleRow("目标方块轮廓",
                "把马上要铺的那一格圈出来",
                () -> AutoBridgeConfig.showOutline, v -> AutoBridgeConfig.showOutline = v));
    }

    // ---------------------------------------------------------------- 行模型

    private interface Item {
    }

    private record GroupHeader(String title) implements Item {
    }

    private abstract static class Row implements Item {
        final String label;
        final String hint;

        Row(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }

        abstract int height();

        /** 命中测试 + 处理点击；返回是否吃掉了这次点击。 */
        abstract boolean click(double mx, double my, int rowX, int rowY, int rowW);
    }

    private static final class ToggleRow extends Row {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;

        ToggleRow(String label, String hint, BooleanSupplier get, Consumer<Boolean> set) {
            super(label, hint);
            this.get = get;
            this.set = set;
        }

        @Override
        int height() {
            return ROW_H;
        }

        @Override
        boolean click(double mx, double my, int rowX, int rowY, int rowW) {
            // 整行可点，点哪儿都切 —— 药丸太小，没必要非点准它
            if (my < rowY || my > rowY + ROW_H || mx < rowX || mx > rowX + rowW) {
                return false;
            }
            set.accept(!get.getAsBoolean());
            AutoBridgeConfig.save();
            return true;
        }
    }

    private final class SliderRow extends Row {
        private final double min;
        private final double max;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final DoubleFunction<String> fmt;

        SliderRow(String label, double min, double max, DoubleSupplier get,
                  DoubleConsumer set, DoubleFunction<String> fmt) {
            super(label, null);
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
            this.fmt = fmt;
        }

        @Override
        int height() {
            return ROW_H;
        }

        String display() {
            return fmt.apply(get.getAsDouble());
        }

        @Override
        boolean click(double mx, double my, int rowX, int rowY, int rowW) {
            if (my < rowY || my > rowY + ROW_H) {
                return false;
            }
            int tw = trackW(rowW);
            int tx = trackX(rowX, rowW);
            // 轨道两侧各放宽 4 px，更容易点中
            if (mx < tx - 4 || mx > tx + tw + 4) {
                return false;
            }
            dragging = this;
            apply(mx, tx, tw);
            return true;
        }

        void apply(double mx, int tx, int tw) {
            double t = (mx - tx) / (double) Math.max(1, tw);
            t = Math.max(0.0D, Math.min(1.0D, t));
            set.accept(min + (max - min) * t);
            AutoBridgeConfig.save();
        }

        static int trackW(int rowW) {
            return Math.min(SLIDER_MAX_W, rowW - 160);
        }

        static int trackX(int rowX, int rowW) {
            return rowX + rowW - trackW(rowW) - 10;
        }
    }

    // ---------------------------------------------------------------- 布局

    @Override
    protected void init() {
        int h = TITLE_H + FOOTER_H + PAD;
        for (Item it : items) {
            h += it instanceof GroupHeader ? GROUP_H : ((Row) it).height();
        }
        panelH = h;
        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max(4, (this.height - panelH) / 2);
    }

    private int rowX() {
        return panelX + PAD;
    }

    private int rowW() {
        return PANEL_W - PAD * 2;
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void extractRenderState(GuiGraphicsExtractor g) {
        // 原版 Screen 的背景（模糊/暗化）
        super.extractRenderState(g);
        // 再压一层，保证面板上的字在任何场景都看得清
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x99000000);

        g.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + panelH + 1, COL_BORDER);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, COL_PANEL);

        int titleW = this.font.width(this.title);
        g.text(this.font, this.title, panelX + (PANEL_W - titleW) / 2, panelY + 7, COL_TITLE);
        g.fill(rowX(), panelY + TITLE_H - 4, rowX() + rowW(), panelY + TITLE_H - 3, 0x30FFFFFF);

        double[] mouse = mousePos();
        int y = panelY + TITLE_H;
        for (Item it : items) {
            if (it instanceof GroupHeader gh) {
                g.text(this.font, gh.title(), rowX() + 2, y + 3, COL_GROUP);
                g.fill(rowX() + 2 + this.font.width(gh.title()) + 6, y + 8,
                        rowX() + rowW(), y + 9, 0x22FFFFFF);
                y += GROUP_H;
                continue;
            }
            Row r = (Row) it;
            boolean hover = mouse[0] >= rowX() && mouse[0] <= rowX() + rowW()
                    && mouse[1] >= y && mouse[1] <= y + r.height();
            if (hover) {
                g.fill(rowX(), y, rowX() + rowW(), y + r.height(), COL_ROW_HOVER);
            }
            drawRow(g, r, y);
            y += r.height();
        }

        drawFooter(g, y + 5, mouse);
    }

    private void drawRow(GuiGraphicsExtractor g, Row r, int y) {
        g.text(this.font, r.label, rowX() + 6, y + 3, COL_TEXT);
        if (r.hint != null) {
            g.text(this.font, r.hint, rowX() + 6, y + 12, COL_HINT);
        }
        if (r instanceof ToggleRow t) {
            drawToggle(g, rowX() + rowW() - TOGGLE_W - 8, y + (ROW_H - TOGGLE_H) / 2,
                    t.get.getAsBoolean());
        } else if (r instanceof SliderRow s) {
            int tw = SliderRow.trackW(rowW());
            int tx = SliderRow.trackX(rowX(), rowW());
            String text = s.display();
            g.text(this.font, text, tx - 8 - this.font.width(text), y + 7, COL_TEXT);
            drawSlider(g, tx, y + ROW_H / 2 - 1, tw, s);
        }
    }

    /** 药丸开关：底槽 + 白色滑块，四角各抠掉 1 px 当作圆角。 */
    private void drawToggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, on ? COL_ON : COL_OFF);
        g.fill(x, y, x + 1, y + 1, COL_PANEL);
        g.fill(x + TOGGLE_W - 1, y, x + TOGGLE_W, y + 1, COL_PANEL);
        g.fill(x, y + TOGGLE_H - 1, x + 1, y + TOGGLE_H, COL_PANEL);
        g.fill(x + TOGGLE_W - 1, y + TOGGLE_H - 1, x + TOGGLE_W, y + TOGGLE_H, COL_PANEL);
        int knobX = on ? x + TOGGLE_W - TOGGLE_H : x;
        g.fill(knobX + 1, y + 1, knobX + TOGGLE_H - 1, y + TOGGLE_H - 1, COL_KNOB);
    }

    /** 细滑轨 + 白色圆点。 */
    private void drawSlider(GuiGraphicsExtractor g, int x, int y, int w, SliderRow s) {
        g.fill(x, y, x + w, y + 3, COL_TRACK);
        double t = (s.get.getAsDouble() - s.min) / Math.max(1.0D, s.max - s.min);
        int filled = (int) Math.round(w * Math.max(0.0D, Math.min(1.0D, t)));
        g.fill(x, y, x + filled, y + 3, COL_TRACK_FILL);
        int kx = x + filled;
        g.fill(kx - 3, y - 2, kx + 3, y + 5, COL_KNOB);
    }

    private void drawFooter(GuiGraphicsExtractor g, int y, double[] mouse) {
        int resetX = panelX + PANEL_W / 2 - BTN_W - 3;
        int doneX = panelX + PANEL_W / 2 + 3;
        button(g, resetX, y, "恢复默认", over(mouse, resetX, y), COL_BTN);
        button(g, doneX, y, "完成", over(mouse, doneX, y), COL_BTN_OK);
    }

    private static boolean over(double[] mouse, int x, int y) {
        return mouse[0] >= x && mouse[0] <= x + BTN_W && mouse[1] >= y && mouse[1] <= y + BTN_H;
    }

    private void button(GuiGraphicsExtractor g, int x, int y, String label, boolean hover, int base) {
        g.fill(x, y, x + BTN_W, y + BTN_H, hover ? lighten(base) : base);
        g.fill(x, y, x + BTN_W, y + 1, 0x30FFFFFF);
        int tw = this.font.width(label);
        g.text(this.font, label, x + (BTN_W - tw) / 2, y + (BTN_H - 8) / 2, 0xFFFFFFFF);
    }

    private static int lighten(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 24);
        int gg = Math.min(255, ((argb >> 8) & 0xFF) + 24);
        int b = Math.min(255, (argb & 0xFF) + 24);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private double[] mousePos() {
        if (this.minecraft == null || this.minecraft.mouseHandler == null
                || this.minecraft.getWindow() == null) {
            return new double[]{-1, -1};
        }
        var win = this.minecraft.getWindow();
        var mh = this.minecraft.mouseHandler;
        return new double[]{mh.getScaledXPos(win), mh.getScaledYPos(win)};
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!event.isLeftMouse()) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();

        int y = panelY + TITLE_H;
        for (Item it : items) {
            if (it instanceof GroupHeader) {
                y += GROUP_H;
                continue;
            }
            Row r = (Row) it;
            if (r.click(mx, my, rowX(), y, rowW())) {
                return true;
            }
            y += r.height();
        }

        int footerY = y + 5;
        int resetX = panelX + PANEL_W / 2 - BTN_W - 3;
        int doneX = panelX + PANEL_W / 2 + 3;
        double[] mouse = {mx, my};
        if (over(mouse, resetX, footerY)) {
            AutoBridgeConfig.resetDefaults();
            AutoBridgeConfig.save();
            return true;
        }
        if (over(mouse, doneX, footerY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging != null) {
            int tw = SliderRow.trackW(rowW());
            int tx = SliderRow.trackX(rowX(), rowW());
            dragging.apply(event.x(), tx, tw);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
