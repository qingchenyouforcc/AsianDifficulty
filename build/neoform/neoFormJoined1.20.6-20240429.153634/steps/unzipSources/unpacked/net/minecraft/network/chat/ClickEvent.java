package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.util.StringRepresentable;

public class ClickEvent {
    public static final Codec<ClickEvent> CODEC = RecordCodecBuilder.create(
        p_304457_ -> p_304457_.group(
                    ClickEvent.Action.CODEC.forGetter(p_304573_ -> p_304573_.action), Codec.STRING.fieldOf("value").forGetter(p_304481_ -> p_304481_.value)
                )
                .apply(p_304457_, ClickEvent::new)
    );
    private final ClickEvent.Action action;
    private final String value;

    public ClickEvent(ClickEvent.Action p_130620_, String p_130621_) {
        this.action = p_130620_;
        this.value = p_130621_;
    }

    public ClickEvent.Action getAction() {
        return this.action;
    }

    public String getValue() {
        return this.value;
    }

    @Override
    public boolean equals(Object p_130625_) {
        if (this == p_130625_) {
            return true;
        } else if (p_130625_ != null && this.getClass() == p_130625_.getClass()) {
            ClickEvent clickevent = (ClickEvent)p_130625_;
            return this.action == clickevent.action && this.value.equals(clickevent.value);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "ClickEvent{action=" + this.action + ", value='" + this.value + "'}";
    }

    @Override
    public int hashCode() {
        int i = this.action.hashCode();
        return 31 * i + this.value.hashCode();
    }

    public static enum Action implements StringRepresentable {
        OPEN_URL("open_url", true),
        OPEN_FILE("open_file", false),
        RUN_COMMAND("run_command", true),
        SUGGEST_COMMAND("suggest_command", true),
        CHANGE_PAGE("change_page", true),
        COPY_TO_CLIPBOARD("copy_to_clipboard", true);

        public static final MapCodec<ClickEvent.Action> UNSAFE_CODEC = StringRepresentable.fromEnum(ClickEvent.Action::values).fieldOf("action");
        public static final MapCodec<ClickEvent.Action> CODEC = UNSAFE_CODEC.validate(ClickEvent.Action::filterForSerialization);
        private final boolean allowFromServer;
        private final String name;

        private Action(String p_130642_, boolean p_130643_) {
            this.name = p_130642_;
            this.allowFromServer = p_130643_;
        }

        public boolean isAllowedFromServer() {
            return this.allowFromServer;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static DataResult<ClickEvent.Action> filterForSerialization(ClickEvent.Action p_304789_) {
            return !p_304789_.isAllowedFromServer() && (net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() == null || net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().isDedicatedServer()) // Neo: Allow open file commands to work on integrated servers. PR #915
                ? DataResult.error(() -> "Action not allowed: " + p_304789_)
                : DataResult.success(p_304789_, Lifecycle.stable());
        }
    }
}
