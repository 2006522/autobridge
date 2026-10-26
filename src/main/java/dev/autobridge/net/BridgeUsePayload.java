package dev.autobridge.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端的心跳：玩家此刻在按右键。
 *
 * <p>为什么需要这个包：服务端<b>没法</b>知道"右键是否按住"。原版对普通方块物品
 * 的右键是即时动作，没有 {@code useDuration}，所以 {@code ServerPlayer#isUsingItem()}
 * 永远是 false；客户端按住右键其实每 4 刻才发一个 {@code useItemOn}，
 * 靠它推断也会漏（准星对着空气时客户端发的是 {@code useItem}，压根不是同一个包）。</p>
 *
 * <p>内容是空的，就是个脉冲；服务端按时间窗口（{@link dev.autobridge.BridgeBuilder}）判断。</p>
 */
public record BridgeUsePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BridgeUsePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.parse("autobridge:use"));

    /** 没有字段，用 {@code StreamCodec.unit} 就是零字节。 */
    public static final StreamCodec<FriendlyByteBuf, BridgeUsePayload> CODEC =
            StreamCodec.unit(new BridgeUsePayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
