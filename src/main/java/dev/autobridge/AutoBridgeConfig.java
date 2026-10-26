package dev.autobridge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * 配置读写，文件在 {@code config/autobridge.properties}。
 *
 * <p>选项很少，所以没做图形界面：客户端按 B 键切总开关（会写回这里），
 * 另外两个选项直接改文件。</p>
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
            try (FileOutputStream fos = new FileOutputStream(FILE)) {
                out.store(fos, "AutoBridge - 自动搭路配置（B 键可切总开关）");
            }
        } catch (Exception e) {
            Log.warn("写入配置失败: " + e.getMessage());
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        String raw = p.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }
}
