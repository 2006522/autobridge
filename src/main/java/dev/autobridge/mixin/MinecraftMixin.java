package dev.autobridge.mixin;

import dev.autobridge.client.AutoBridgeClient;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在"开始使用物品"这一刻给服务端发心跳。
 *
 * <p>必须是 HEAD：原版紧接着就会发出自己的 {@code useItemOn} 包，而服务端要靠心跳
 * 判断"要不要拦下这次放置"。心跳先到，顺序才对；放到 tick 末尾发就会慢一拍，
 * 第一次右键会让原版多放一块。</p>
 *
 * <p>{@code startUseItem} 只在按住右键、且右键冷却（4 刻）到了的时候才调用，
 * 所以它天然就是"玩家按住右键"的节拍器，不用自己算时间。</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void autobridge$sendUseSignal(CallbackInfo ci) {
        AutoBridgeClient.sendUseSignal();
    }
}
