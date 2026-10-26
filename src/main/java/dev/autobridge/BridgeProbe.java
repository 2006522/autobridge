package dev.autobridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 「现在这一格该不该补、手头有没有砖」的纯判断逻辑。
 *
 * <p>单独拎出来是为了让<b>客户端</b>也能算：准星下面那个倒三角要显示
 * 「马上就会铺」还是「暂时不会铺」，得用和服务端完全一样的条件来判断，
 * 否则提示和实际行为会对不上。这里刻意只依赖 {@link Player} / {@link Level}，
 * 不碰 {@code ServerPlayer} / {@code ServerLevel} —— 客户端加载这个类时不会连带
 * 加载服务端专属类型。</p>
 */
public final class BridgeProbe {

    /** 判定"在移动"的水平速度阈值（格/刻）。走路约 0.215。 */
    private static final double MOVE_EPSILON = 0.02D;

    private BridgeProbe() {
    }

    // ---------------------------------------------------------------- 位置判断

    /** 玩家站的那一层、朝向前方一格（也就是下一步要踩的那块）。 */
    public static BlockPos frontPos(Player player) {
        return player.blockPosition().relative(forward(player)).below();
    }

    /**
     * "前方"取哪个方向：优先用<b>实际移动方向</b>，没在动才用朝向。
     *
     * <p>只按朝向算的话，侧身沿着崖边平移（很常见的搭桥姿势）会把桥修到自己侧面去；
     * 按速度算才是"你往哪走就往哪铺"。速度抖动/为 0 时回退到朝向。</p>
     */
    public static Direction forward(Player player) {
        Vec3 velocity = player.getDeltaMovement();
        // getNearest 比的是整数分量的绝对值，放大一千倍才分得出主次
        int x = (int) Math.round(velocity.x * 1000.0D);
        int z = (int) Math.round(velocity.z * 1000.0D);
        if (x == 0 && z == 0) {
            return player.getDirection();
        }
        return Direction.getNearest(x, 0, z, player.getDirection());
    }

    public static boolean isMoving(Player player) {
        Vec3 velocity = player.getDeltaMovement();
        return Math.abs(velocity.x) + Math.abs(velocity.z) > MOVE_EPSILON;
    }

    /** 脚前那一格是否需要补（只看位置，不看材料）。 */
    public static boolean needsBridge(Player player) {
        if (!AutoBridgeConfig.enable) {
            return false;
        }
        Level level = player.level();
        if (level == null || !player.isAlive()) {
            return false;
        }
        if (AutoBridgeConfig.onlyWhenSneaking && !player.isShiftKeyDown()) {
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

    /** 位置合适、又有砖 —— 按下去立刻就会铺。 */
    public static boolean ready(Player player) {
        return needsBridge(player) && material(player) != null;
    }

    // ---------------------------------------------------------------- 材料

    /** 一块砖：物品 + 在哪个手上。 */
    public record Material(ItemStack stack, InteractionHand hand) {
    }

    /** 主手 → 副手 → 快捷栏，找第一个能当砖的方块。 */
    public static Material material(Player player) {
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

    private static boolean usable(Player player, ItemStack stack) {
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
