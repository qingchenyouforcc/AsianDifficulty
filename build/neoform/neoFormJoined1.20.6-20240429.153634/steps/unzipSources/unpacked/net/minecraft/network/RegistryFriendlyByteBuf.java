package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.minecraft.core.RegistryAccess;

public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    private final RegistryAccess registryAccess;
    private final net.neoforged.neoforge.network.connection.ConnectionType connectionType;

    /**
     * @deprecated Neo: use overload with ConnectionType context
     */
    @Deprecated
    public RegistryFriendlyByteBuf(ByteBuf p_320951_, RegistryAccess p_319803_) {
        super(p_320951_);
        this.registryAccess = p_319803_;
        this.connectionType = net.neoforged.neoforge.network.connection.ConnectionType.OTHER;
    }

    public RegistryFriendlyByteBuf(ByteBuf p_320951_, RegistryAccess p_319803_, net.neoforged.neoforge.network.connection.ConnectionType connectionType) {
        super(p_320951_);
        this.registryAccess = p_319803_;
        this.connectionType = connectionType;
    }

    public net.neoforged.neoforge.network.connection.ConnectionType getConnectionType() {
        return this.connectionType;
    }

    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public static Function<ByteBuf, RegistryFriendlyByteBuf> decorator(RegistryAccess p_320166_, net.neoforged.neoforge.network.connection.ConnectionType connectionType) {
        return p_320793_ -> new RegistryFriendlyByteBuf(p_320793_, p_320166_, connectionType);
    }

    /**
     * @deprecated Neo: use overload with ConnectionType context
     */
    @Deprecated
    public static Function<ByteBuf, RegistryFriendlyByteBuf> decorator(RegistryAccess p_320166_) {
        return p_320793_ -> new RegistryFriendlyByteBuf(p_320793_, p_320166_);
    }
}
