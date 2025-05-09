package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatType;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class ObjectiveCriteriaArgument implements ArgumentType<ObjectiveCriteria> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo.bar.baz", "minecraft:foo");
    public static final DynamicCommandExceptionType ERROR_INVALID_VALUE = new DynamicCommandExceptionType(
        p_304092_ -> Component.translatableEscape("argument.criteria.invalid", p_304092_)
    );

    private ObjectiveCriteriaArgument() {
    }

    public static ObjectiveCriteriaArgument criteria() {
        return new ObjectiveCriteriaArgument();
    }

    public static ObjectiveCriteria getCriteria(CommandContext<CommandSourceStack> p_102566_, String p_102567_) {
        return p_102566_.getArgument(p_102567_, ObjectiveCriteria.class);
    }

    public ObjectiveCriteria parse(StringReader p_102560_) throws CommandSyntaxException {
        int i = p_102560_.getCursor();

        while (p_102560_.canRead() && p_102560_.peek() != ' ') {
            p_102560_.skip();
        }

        String s = p_102560_.getString().substring(i, p_102560_.getCursor());
        return ObjectiveCriteria.byName(s).orElseThrow(() -> {
            p_102560_.setCursor(i);
            return ERROR_INVALID_VALUE.createWithContext(p_102560_, s);
        });
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> p_102572_, SuggestionsBuilder p_102573_) {
        List<String> list = Lists.newArrayList(ObjectiveCriteria.getCustomCriteriaNames());

        for (StatType<?> stattype : BuiltInRegistries.STAT_TYPE) {
            for (Object object : stattype.getRegistry()) {
                String s = this.getName(stattype, object);
                list.add(s);
            }
        }

        return SharedSuggestionProvider.suggest(list, p_102573_);
    }

    public <T> String getName(StatType<T> p_102557_, Object p_102558_) {
        return Stat.buildName(p_102557_, (T)p_102558_);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
