package dev.autobridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动搭路的核心：算出"脚前那一格"，用原版放置逻辑把方块放上去。
 *
 * <p><b>为什么是脚前那一格而不是准星那一格</b>：原版倒退搭桥要蹲下、准星对着脚下方块、
 * 一边退一边点，本质上就是"在身前缺的那一格里放方块"。这条路直接把它自动化 ——
 * 玩家正常朝前走，只要按住右键，缺的那一格就会补上，不用回头也不用蹲。</p>
 *
 * <p><b>严格的触发条件</b>（任何一条不满足就完全不介入，交还原版）：
 * <ol>
 *   <li>总开关开着，且（默认）玩家正在移动；</li>
 *   <li>脚前那一格是空气/可替换；</li>
 *   <li>脚前那一格<strong>下面也是空的</strong> —— 否则说明前面本来就有地面或是台阶，
 *       根本不需要铺；</li>
 *   <li>手头/快捷栏里找得到能当砖的方块。</li>
 * </ol>
 * 所以正常站在地上走、对着墙放方块、方块用完了这些情况都不会被影响。</p>
 */
public final class BridgeBuilder {

    /** 客户端每 4 刻发一次心跳（约 200ms），窗口给到 350ms 免得网络抖动断档。 */
    private static final long WINDOW_MS = 350L;

    /** 判定"在移动"的水平速度阈值（格/刻）。走路约 0.215。 */
    private static final double MOVE_EPSILON = 0.02D;

    private static final Map<UUID, Long> SIGNAL = new ConcurrentHashMap<>();

    private BridgeBuilder() {
    }

    // ---------------------------------------------------------------- 信号

    /** 收到客户端心跳：记时间戳，顺手铺一格（不用等服务端 tick）。 */
    public static void onSignal(ServerPlayer player) {
        SIGNAL.put(player.getUUID(), System.currentTimeMillis());
        tryBuild(player);
    }

    /** 服务端每刻兜底：窗口期内每刻都尝试铺，这样跟得上步行速度。 */
    public static void tick(ServerPlayer player) {
        Long at = SIGNAL.get(player.getUUID());
        if (at == null) {
            return;
        }
        if (System.currentTimeMillis() - at > WINDOW_MS) {
            SIGNAL.remove(player.getUUID());
            return;
        }
        tryBuild(player);
    }

    public static void forget(ServerPlayer player) {
        SIGNAL.remove(player.getUUID());
    }

    private static boolean active(ServerPlayer player) {
        Long at = SIGNAL.get(player.getUUID());
        return at != null && System.currentTimeMillis() - at <= WINDOW_MS;
    }

    // ---------------------------------------------------------------- 位置判断

    /** 玩家站的那一层、朝向前方一格（也就是下一步要踩的那块）。 */
    public static BlockPos frontPos(ServerPlayer player) {
        return player.blockPosition().relative(player.getDirection()).below();
    }

    /** 脚前那一格是否需要补。 */
    public static boolean needsBridge(ServerPlayer player) {
        if (!AutoBridgeConfig.enable) {
            return false;
        }
        ServerLevel level = player.level();
        if (level == null || !player.isAlive()) {
            return false;
        }
        if (AutoBridgeConfig.needMoving && !isMoving(player)) {
            return false;
        }
        BlockPos target = frontPos(player);
        if (!level.getBlockState(target).canBeReplaced()) {
            return false; // 前面已经站得住了
        }
        // 前方下面还有地面 → 那是平地/台阶，不需要铺
        return level.getBlockState(target.below()).isAir();
    }

    private static boolean isMoving(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        return Math.abs(velocity.x) + Math.abs(velocity.z) > MOVE_EPSILON;
    }

    /**
     * 服务端要不要拦下这次原版右键放置。
     *
     * <p>拦下来的理由是：搭路时玩家准星往往对着前方的地面，原版会把手里的方块
     * 摆到"那格地面上面"，而我们要摆的是脚前那一格 —— 不拦就会一次放两块。</p>
     */
    public static boolean shouldSuppress(ServerPlayer player) {
        return active(player) && needsBridge(player) && findMaterial(player) != null;
    }

    // ---------------------------------------------------------------- 放置

    /** 尝试铺一格，返回是否真的放下了。 */
    public static boolean tryBuild(ServerPlayer player) {
        if (!needsBridge(player)) {
            return false;
        }
        Material material = findMaterial(player);
        if (material == null) {
            return false;
        }
        ServerLevel level = player.level();
        BlockPos target = frontPos(player);
        Direction dir = player.getDirection();

        // 支撑面：脚前那格的"这边那格"朝 dir 的那个面 —— 也就是玩家正踩着的那块。
        // 交给原版 BlockPlaceContext 去判支撑，相邻半空中没有依附会自己失败，不会凭空造方块。
        BlockPos support = target.relative(dir.getOpposite());
        Vec3 click = Vec3.atCenterOf(support).relative(dir, 0.5D);
        BlockHitResult hit = new BlockHitResult(click, dir, support, false);
        BlockPlaceContext context = new BlockPlaceContext(player, material.hand(), material.stack(), hit);
        // 确认原版算出来的落点就是我们要补的那一格（万一被"可替换"之类的规则挪走，就不放）
        if (!context.getClickedPos().relative(context.getClickedFace()).equals(target)) {
            return false;
        }

        try {
            // 原版放置：朝向、形状、支撑判定、扣物品、音效、统计全部沿用
            InteractionResult result = ((BlockItem) material.stack().getItem()).place(context);
            return result != null && result.consumesAction();
        } catch (Exception e) {
            Log.error("自动搭路放置方块失败", e);
            return false;
        }
    }

    /** 一块砖：物品 + 在哪个手上。 */
    private record Material(ItemStack stack, InteractionHand hand) {
    }

    /** 主手 → 副手 → 快捷栏，找第一个能当砖的方块。 */
    private static Material findMaterial(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (usable(player, main)) {
            return new Material(main, InteractionHand.MAIN_HAND);
        }
        ItemStack off = player.getOffhandItem();
        if (usable(player, off)) {
            return new Material(off, InteractionHand.OFF_HAND);
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (usable(player, stack)) {
                // 快捷栏里的方块：手不在它上面，但普通方块的 place 只用 getItemInHand()，
                // 传 MAIN_HAND 即可正确扣掉这个 stack
                return new Material(stack, InteractionHand.MAIN_HAND);
            }
        }
        return null;
    }

    private static boolean usable(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (!AutoBridgeConfig.requireFullBlock) {
            return true;
        }
        // 种子、树苗、火把、台阶这些虽然能放，但拿来铺路既浪费又站不稳
        BlockState state = blockItem.getBlock().defaultBlockState();
        return state.isCollisionShapeFullBlock(player.level(), player.blockPosition());
    }
}
