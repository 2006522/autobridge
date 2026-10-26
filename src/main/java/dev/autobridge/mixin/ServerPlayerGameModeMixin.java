package dev.autobridge.mixin;

import dev.autobridge.BridgeBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 搭路期间拦下原版的右键放置，避免"一次放两块"。
 *
 * <p>为什么会被放两块：搭路时玩家准星多半对着前方地面，原版会把方块摆到
 * "那格地面上面"；我们同时又会把方块摆到脚前那一格。两处不一定是同一格，
 * 所以玩家的方块会多消耗一个、位置也乱。这里在搭路条件成立时直接吃掉这次右键，
 * 只让 {@link BridgeBuilder} 去放。</p>
 *
 * <p>只在服务端生效；客户端那边照旧做预测（预测错一帧无所谓，服务端会纠正）。</p>
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void autobridge$suppressVanillaPlacement(ServerPlayer player, Level level, ItemStack stack,
                                                     InteractionHand hand, BlockHitResult hitResult,
                                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (BridgeBuilder.shouldSuppress(this.player)) {
            // 当成"处理过了"返回，客户端不会退回去再试 useItem，手臂也照常挥一下
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
