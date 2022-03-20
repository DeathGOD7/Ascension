/*
 * This file is part of DiscordSRV, licensed under the GPLv3 License
 * Copyright (c) 2016-2022 Austin "Scarsz" Shapiro, Henri "Vankka" Schubin and DiscordSRV contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.discordsrv.common.command.game.handler.util;

import com.discordsrv.common.command.game.abstraction.GameCommand;
import com.discordsrv.common.command.game.abstraction.GameCommandExecutor;
import com.discordsrv.common.command.game.abstraction.GameCommandSuggester;
import com.discordsrv.common.command.game.sender.ICommandSender;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Helper class to convert DiscordSRV's abstract command tree into a brigadier one.
 */
public final class BrigadierUtil {

    private static final Map<GameCommand, CommandNode<?>> CACHE = new ConcurrentHashMap<>();

    private BrigadierUtil() {}

    public static <S> LiteralCommandNode<S> convertToBrigadier(GameCommand command, Function<S, ICommandSender> commandSenderMapper) {
        return (LiteralCommandNode<S>) convert(command, commandSenderMapper);
    }

    @SuppressWarnings("unchecked")
    private static <S> CommandNode<S> convert(GameCommand commandBuilder, Function<S, ICommandSender> commandSenderMapper) {
        CommandNode<S> alreadyConverted = (CommandNode<S>) CACHE.get(commandBuilder);
        if (alreadyConverted != null) {
            return alreadyConverted;
        }

        GameCommand.ArgumentType type = commandBuilder.getArgumentType();
        String label = commandBuilder.getLabel();
        GameCommandExecutor executor = commandBuilder.getExecutor();
        GameCommandSuggester suggester = commandBuilder.getSuggester();
        GameCommand redirection = commandBuilder.getRedirection();
        String requiredPermission = commandBuilder.getRequiredPermission();

        ArgumentBuilder<S, ?> argumentBuilder;
        if (type == GameCommand.ArgumentType.LITERAL) {
            argumentBuilder = LiteralArgumentBuilder.literal(label);
        } else {
            argumentBuilder = RequiredArgumentBuilder.argument(label, convertType(commandBuilder));
        }

        for (GameCommand child : commandBuilder.getChildren()) {
            argumentBuilder.then(convert(child, commandSenderMapper));
        }
        if (redirection != null) {
            CommandNode<S> redirectNode = (CommandNode<S>) CACHE.get(redirection);
            if (redirectNode == null) {
                redirectNode = convert(redirection, commandSenderMapper);
            }
            argumentBuilder.redirect(redirectNode);
        }

        if (requiredPermission != null) {
            argumentBuilder.requires(sender -> {
                ICommandSender commandSender = commandSenderMapper.apply(sender);
                return commandSender.hasPermission(requiredPermission);
            });
        }
        if (executor != null) {
            argumentBuilder.executes(context -> {
                executor.execute(
                        commandSenderMapper.apply(context.getSource()),
                        context::getArgument
                );
                return Command.SINGLE_SUCCESS;
            });
        }
        if (suggester != null && argumentBuilder instanceof RequiredArgumentBuilder) {
            ((RequiredArgumentBuilder<S, ?>) argumentBuilder).suggests((context, builder) -> {
                try {
                    List<?> suggestions =  suggester.suggestValues(
                            commandSenderMapper.apply(context.getSource()),
                            context::getArgument,
                            builder.getRemaining()
                    );
                    suggestions.forEach(suggestion -> builder.suggest(suggestion.toString()));
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                return CompletableFuture.completedFuture(builder.build());
            });
        }

        CommandNode<S> node = argumentBuilder.build();
        CACHE.put(commandBuilder, node);
        return node;
    }

    private static ArgumentType<?> convertType(GameCommand builder) {
        GameCommand.ArgumentType argumentType = builder.getArgumentType();
        double min = builder.getMinValue();
        double max = builder.getMaxValue();
        switch (argumentType) {
            case LONG: return LongArgumentType.longArg((long) min, (long) max);
            case FLOAT: return FloatArgumentType.floatArg((float) min, (float) max);
            case DOUBLE: return DoubleArgumentType.doubleArg(min, max);
            case STRING: return StringArgumentType.string();
            case STRING_WORD: return StringArgumentType.word();
            case STRING_GREEDY: return StringArgumentType.greedyString();
            case BOOLEAN: return BoolArgumentType.bool();
            case INTEGER: return IntegerArgumentType.integer((int) min, (int) max);
        }
        throw new IllegalStateException();
    }
}
