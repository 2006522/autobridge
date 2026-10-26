package dev.autobridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * 配置读写，文件在 {@code config/autobridge.properties}。
 *
 * <p>游戏内界面在 {@code dev.autobridge.client.ConfigScreen}，按 {@code H} 或点 ⚙ 打开；
 * 这个文件就是界面读写的同一份数据，手改也生效（下次启动读取）。</p>
 */
public final class AutoBridgeConfig {

    public static final File FILE = new File(new File("config"), "autobridge.properties");

    /** 总开关。 */
    public static boolean enable = true;

    /**
     * 只在移动时自动铺路。
     *
     * <p>关掉的话站着不动也会铺（一按右键就往脚前放一格）；开着更安全 ——
     * 站在崖边对墙放方块还是原版行为。</p>
     */
    public static boolean needMoving = true;

    /**
     * 只拿"完整碰撞体积"的方块当铺路材料。
     *
     * <p>背包里挑材料时，种子、树苗、火把、台阶、按钮这些虽然也是 {@code BlockItem}，
     * 但拿来铺路是浪费（而且站不住）。开着就只认完整方块。</p>
     */
    public static boolean requireFullBlock = true;

    /**
     * 准星下方那个倒三角提示。
     *
     * <p>亮着 = 现在按住右键就会立刻铺；暗着 = 暂时不会铺（没在动 / 前面有地面 /
     * 手头没有能当砖的方块）。觉得挡视线可以关掉。</p>
     */
    public static boolean crosshairHint = true;

    /** 准星三角的颜色（ARGB，不带 alpha 时按不透明处理）。 */
    public static int hintColor = 0x5CDBFF;

    /**
     * 显示"马上要铺的那一格"的方块轮廓。
     *
     * <p>和三角互补：三角只说"会不会铺"，轮廓直接指给人看"铺哪儿"。</p>
     */
    public static boolean showOutline = true;

    /** 轮廓颜色（ARGB）。 */
    public static int outlineColor = 0x5CDBFF;

    /**
     * 两次铺路之间至少间隔多少刻（20 刻 = 1 秒）。
     *
     * <p>0 = 每个服务端 tick 都尝试（最快）；调大可以放慢铺路节奏，
     * 手抖或者网卡时不会一下子铺出去好几格。</p>
     */
    public static int placeDelay = 0;

    /**
     * 只在潜行时自动铺路。
     *
     * <p>比 {@link #needMoving} 更严：开着的话必须按住潜行才会铺。
     * 适合"平时正常玩、需要搭桥时蹲下走"的习惯。</p>
     */
    public static boolean onlyWhenSneaking = false;

    private static boolean loaded;

    private AutoBridgeConfig() {
    }

    public static void loadIfNeeded() {
        if (loaded) {
            return;
        }
        reload();
    }

    /** 重新从磁盘加载。 */
    public static void reload() {
        loaded = true;
        Properties p = new Properties();
        if (FILE.isFile()) {
            try (FileInputStream in = new FileInputStream(FILE)) {
                p.load(in);
            } catch (Exception e) {
                Log.warn("读取配置失败，使用默认值: " + e.getMessage());
            }
        }
        enable = bool(p, "enable", enable);
        needMoving = bool(p, "need_moving", needMoving);
        requireFullBlock = bool(p, "require_full_block", requireFullBlock);
        crosshairHint = bool(p, "crosshair_hint", crosshairHint);
        hintColor = color(p, "hint_color", hintColor);
        showOutline = bool(p, "show_outline", showOutline);
        outlineColor = color(p, "outline_color", outlineColor);
        placeDelay = clampInt(p, "place_delay", placeDelay, 0, 20);
        onlyWhenSneaking = bool(p, "only_when_sneaking", onlyWhenSneaking);

        if (!FILE.isFile()) {
            save();
        }
    }

    /** 把当前内存中的配置写回文件。 */
    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            Properties out = new Properties();
            out.setProperty("enable", Boolean.toString(enable));
            out.setProperty("need_moving", Boolean.toString(needMoving));
            out.setProperty("require_full_block", Boolean.toString(requireFullBlock));
            out.setProperty("crosshair_hint", Boolean.toString(crosshairHint));
            out.setProperty("hint_color", hex(hintColor));
            out.setProperty("show_outline", Boolean.toString(showOutline));
            out.setProperty("outline_color", hex(outlineColor));
            out.setProperty("place_delay", Integer.toString(placeDelay));
            out.setProperty("only_when_sneaking", Boolean.toString(onlyWhenSneaking));
            try (FileOutputStream fos = new FileOutputStream(FILE)) {
                out.store(fos, "AutoBridge - 自动搭路配置（游戏内按 H 打开设置界面）");
            }
        } catch (Exception e) {
            Log.warn("写入配置失败: " + e.getMessage());
        }
    }

    /** 恢复默认值（不动文件，调用方决定什么时候 save）。 */
    public static void resetDefaults() {
        enable = true;
        needMoving = true;
        requireFullBlock = true;
        crosshairHint = true;
        hintColor = 0x5CDBFF;
        showOutline = true;
        outlineColor = 0x5CDBFF;
        placeDelay = 0;
        onlyWhenSneaking = false;
    }

    // ---------------------------------------------------------------- 读写辅助

    private static boolean bool(Properties p, String key, boolean fallback) {
        String raw = p.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private static int clampInt(Properties p, String key, int fallback, int min, int max) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 读颜色。支持 {@code 0xRRGGBB} / {@code #RRGGBB} / 十进制。
     *
     * <p>不带 alpha 的一律补成不透明 —— 配置文件里手写颜色时很难记得填 alpha，
     * 少了它就整条看不见了。</p>
     */
    private static int color(Properties p, String key, int fallback) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim();
        try {
            int v;
            if (s.startsWith("#")) {
                v = (int) Long.parseLong(s.substring(1), 16);
            } else if (s.startsWith("0x") || s.startsWith("0X")) {
                v = (int) Long.parseLong(s.substring(2), 16);
            } else {
                v = (int) Long.parseLong(s);
            }
            if ((v & 0xFF000000) == 0) {
                v |= 0xFF000000;
            }
            return v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String hex(int argb) {
        return String.format("0x%08X", argb);
    }
}
