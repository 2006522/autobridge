package dev.autobridge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.autobridge.AutoBridgeConfig;
import dev.autobridge.BridgeProbe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给"马上要铺的那一格"画一圈方块轮廓。
 *
 * <p>那一格现在是空气，没有自己的碰撞形状，所以直接用一个完整立方体的
 * {@link Shapes#block()} 当轮廓 —— 看起来就是"这里会多一块"。</p>
 *
 * <p>只在 client mixins 里注册（引用了客户端渲染类）。</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    /** 复用原版的轮廓提交方法（private，用 @Shadow 假体引用）。 */
    @Shadow
    private void submitHitOutline(PoseStack poseStack, SubmitNodeCollector collector,
                                  net.minecraft.client.renderer.rendertype.RenderType renderType,
                                  BlockOutlineRenderState state, int color, float lineWidth,
                                  boolean translucent) {
        throw new AssertionError();
    }

    @Inject(method = "submitBlockOutline", at = @At("TAIL"))
    private void autobridge$submitTargetOutline(PoseStack poseStack, SubmitNodeCollector collector,
                                                LevelRenderState levelState, CallbackInfo ci) {
        if (!AutoBridgeConfig.enable || !AutoBridgeConfig.showOutline) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || levelState.cameraRenderState == null) {
            return;
        }
        // 只有"这一格真会铺"的时候才画 —— 和三角用同一份判定，不会指着一个铺不了的位置
        if (!BridgeProbe.needsBridge(mc.player)) {
            return;
        }
        BlockPos target = BridgeProbe.frontPos(mc.player);
        VoxelShape shape = Shapes.block();
        var cam = levelState.cameraRenderState.pos;

        BlockOutlineRenderState st = new BlockOutlineRenderState(target.immutable(), false, false, shape);
        poseStack.pushPose();
        poseStack.translate(target.getX() - cam.x, target.getY() - cam.y, target.getZ() - cam.z);
        this.submitHitOutline(poseStack, collector, RenderTypes.lines(), st,
                AutoBridgeConfig.outlineColor, 2.0F, false);
        poseStack.popPose();
    }
}
