package net.minecraft.resources;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Type;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;
import org.apache.commons.lang3.StringUtils;

public class ResourceLocation implements Comparable<ResourceLocation> {
    public static final Codec<ResourceLocation> CODEC = Codec.STRING
        .<ResourceLocation>comapFlatMap(ResourceLocation::read, ResourceLocation::toString)
        .stable();
    public static final StreamCodec<ByteBuf, ResourceLocation> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(ResourceLocation::new, ResourceLocation::toString);
    public static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.id.invalid"));
    public static final char NAMESPACE_SEPARATOR = ':';
    public static final String DEFAULT_NAMESPACE = "minecraft";
    public static final String REALMS_NAMESPACE = "realms";
    private final String namespace;
    private final String path;

    protected ResourceLocation(String p_248791_, String p_249394_, @Nullable ResourceLocation.Dummy p_249089_) {
        this.namespace = p_248791_;
        this.path = p_249394_;
    }

    public ResourceLocation(String p_135811_, String p_135812_) {
        this(assertValidNamespace(p_135811_, p_135812_), assertValidPath(p_135811_, p_135812_), null);
    }

    private ResourceLocation(String[] p_135814_) {
        this(p_135814_[0], p_135814_[1]);
    }

    public ResourceLocation(String p_135809_) {
        this(decompose(p_135809_, ':'));
    }

    public static ResourceLocation of(String p_135823_, char p_135824_) {
        return new ResourceLocation(decompose(p_135823_, p_135824_));
    }

    @Nullable
    public static ResourceLocation tryParse(String p_135821_) {
        try {
            return new ResourceLocation(p_135821_);
        } catch (ResourceLocationException resourcelocationexception) {
            return null;
        }
    }

    @Nullable
    public static ResourceLocation tryBuild(String p_214294_, String p_214295_) {
        try {
            return new ResourceLocation(p_214294_, p_214295_);
        } catch (ResourceLocationException resourcelocationexception) {
            return null;
        }
    }

    protected static String[] decompose(String p_135833_, char p_135834_) {
        String[] astring = new String[]{"minecraft", p_135833_};
        int i = p_135833_.indexOf(p_135834_);
        if (i >= 0) {
            astring[1] = p_135833_.substring(i + 1);
            if (i >= 1) {
                astring[0] = p_135833_.substring(0, i);
            }
        }

        return astring;
    }

    public static DataResult<ResourceLocation> read(String p_135838_) {
        try {
            return DataResult.success(new ResourceLocation(p_135838_));
        } catch (ResourceLocationException resourcelocationexception) {
            return DataResult.error(() -> "Not a valid resource location: " + p_135838_ + " " + resourcelocationexception.getMessage());
        }
    }

    public String getPath() {
        return this.path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public ResourceLocation withPath(String p_251088_) {
        return new ResourceLocation(this.namespace, assertValidPath(this.namespace, p_251088_), null);
    }

    public ResourceLocation withPath(UnaryOperator<String> p_250342_) {
        return this.withPath(p_250342_.apply(this.path));
    }

    public ResourceLocation withPrefix(String p_250620_) {
        return this.withPath(p_250620_ + this.path);
    }

    public ResourceLocation withSuffix(String p_266769_) {
        return this.withPath(this.path + p_266769_);
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.path;
    }

    @Override
    public boolean equals(Object p_135846_) {
        if (this == p_135846_) {
            return true;
        } else {
            return !(p_135846_ instanceof ResourceLocation resourcelocation)
                ? false
                : this.namespace.equals(resourcelocation.namespace) && this.path.equals(resourcelocation.path);
        }
    }

    @Override
    public int hashCode() {
        return 31 * this.namespace.hashCode() + this.path.hashCode();
    }

    public int compareTo(ResourceLocation p_135826_) {
        int i = this.path.compareTo(p_135826_.path);
        if (i == 0) {
            i = this.namespace.compareTo(p_135826_.namespace);
        }

        return i;
    }

    // Normal compare sorts by path first, this compares namespace first.
    public int compareNamespaced(ResourceLocation o) {
        int ret = this.namespace.compareTo(o.namespace);
        return ret != 0 ? ret : this.path.compareTo(o.path);
    }

    public String toDebugFileName() {
        return this.toString().replace('/', '_').replace(':', '_');
    }

    public String toLanguageKey() {
        return this.namespace + "." + this.path;
    }

    public String toShortLanguageKey() {
        return this.namespace.equals("minecraft") ? this.path : this.toLanguageKey();
    }

    public String toLanguageKey(String p_214297_) {
        return p_214297_ + "." + this.toLanguageKey();
    }

    public String toLanguageKey(String p_270871_, String p_270199_) {
        return p_270871_ + "." + this.toLanguageKey() + "." + p_270199_;
    }

    private static String readGreedy(StringReader p_335690_) {
        int i = p_335690_.getCursor();

        while (p_335690_.canRead() && isAllowedInResourceLocation(p_335690_.peek())) {
            p_335690_.skip();
        }

        return p_335690_.getString().substring(i, p_335690_.getCursor());
    }

    public static ResourceLocation read(StringReader p_135819_) throws CommandSyntaxException {
        int i = p_135819_.getCursor();
        String s = readGreedy(p_135819_);

        try {
            return new ResourceLocation(s);
        } catch (ResourceLocationException resourcelocationexception) {
            p_135819_.setCursor(i);
            throw ERROR_INVALID.createWithContext(p_135819_);
        }
    }

    public static ResourceLocation readNonEmpty(StringReader p_336027_) throws CommandSyntaxException {
        int i = p_336027_.getCursor();
        String s = readGreedy(p_336027_);
        if (s.isEmpty()) {
            throw ERROR_INVALID.createWithContext(p_336027_);
        } else {
            try {
                return new ResourceLocation(s);
            } catch (ResourceLocationException resourcelocationexception) {
                p_336027_.setCursor(i);
                throw ERROR_INVALID.createWithContext(p_336027_);
            }
        }
    }

    public static boolean isAllowedInResourceLocation(char p_135817_) {
        return p_135817_ >= '0' && p_135817_ <= '9'
            || p_135817_ >= 'a' && p_135817_ <= 'z'
            || p_135817_ == '_'
            || p_135817_ == ':'
            || p_135817_ == '/'
            || p_135817_ == '.'
            || p_135817_ == '-';
    }

    public static boolean isValidPath(String p_135842_) {
        for (int i = 0; i < p_135842_.length(); i++) {
            if (!validPathChar(p_135842_.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidNamespace(String p_135844_) {
        for (int i = 0; i < p_135844_.length(); i++) {
            if (!validNamespaceChar(p_135844_.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static String assertValidNamespace(String p_250769_, String p_249616_) {
        if (!isValidNamespace(p_250769_)) {
            throw new ResourceLocationException("Non [a-z0-9_.-] character in namespace of location: " + p_250769_ + ":" + p_249616_);
        } else {
            return p_250769_;
        }
    }

    public static boolean validPathChar(char p_135829_) {
        return p_135829_ == '_'
            || p_135829_ == '-'
            || p_135829_ >= 'a' && p_135829_ <= 'z'
            || p_135829_ >= '0' && p_135829_ <= '9'
            || p_135829_ == '/'
            || p_135829_ == '.';
    }

    public static boolean validNamespaceChar(char p_135836_) {
        return p_135836_ == '_' || p_135836_ == '-' || p_135836_ >= 'a' && p_135836_ <= 'z' || p_135836_ >= '0' && p_135836_ <= '9' || p_135836_ == '.';
    }

    public static boolean isValidResourceLocation(String p_135831_) {
        String[] astring = decompose(p_135831_, ':');
        return isValidNamespace(StringUtils.isEmpty(astring[0]) ? "minecraft" : astring[0]) && isValidPath(astring[1]);
    }

    private static String assertValidPath(String p_251418_, String p_248828_) {
        if (!isValidPath(p_248828_)) {
            throw new ResourceLocationException("Non [a-z0-9/._-] character in path of location: " + p_251418_ + ":" + p_248828_);
        } else {
            return p_248828_;
        }
    }

    protected interface Dummy {
    }

    public static class Serializer implements JsonDeserializer<ResourceLocation>, JsonSerializer<ResourceLocation> {
        public ResourceLocation deserialize(JsonElement p_135851_, Type p_135852_, JsonDeserializationContext p_135853_) throws JsonParseException {
            return new ResourceLocation(GsonHelper.convertToString(p_135851_, "location"));
        }

        public JsonElement serialize(ResourceLocation p_135855_, Type p_135856_, JsonSerializationContext p_135857_) {
            return new JsonPrimitive(p_135855_.toString());
        }
    }
}
