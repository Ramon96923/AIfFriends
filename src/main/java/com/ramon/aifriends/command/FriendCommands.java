package com.ramon.aifriends.command;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.ramon.aifriends.entity.FriendEntity;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

public final class FriendCommands {
	private FriendCommands() {
	}

	private static final SuggestionProvider<CommandSourceStack> WHO = (ctx, builder) -> {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null) return builder.buildFuture();
		return SharedSuggestionProvider.suggest(FriendManager.selectorNames(player), builder);
	};

	private static final SuggestionProvider<CommandSourceStack> ORDERS =
			(ctx, builder) -> SharedSuggestionProvider.suggest(FriendManager.ORDERS, builder);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("friend")
				.executes(FriendCommands::help)
				.then(Commands.literal("help").executes(FriendCommands::help))
				.then(Commands.literal("list").executes(FriendCommands::list))
				.then(Commands.literal("spawn")
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(ctx -> FriendManager.spawn(player(ctx), StringArgumentType.getString(ctx, "name"), 1, null))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, FriendManager.MAX_SPAWN))
										.executes(ctx -> FriendManager.spawn(player(ctx), StringArgumentType.getString(ctx, "name"),
												IntegerArgumentType.getInteger(ctx, "count"), null))
										.then(Commands.argument("skin", StringArgumentType.word())
												.executes(ctx -> FriendManager.spawn(player(ctx), StringArgumentType.getString(ctx, "name"),
														IntegerArgumentType.getInteger(ctx, "count"), StringArgumentType.getString(ctx, "skin")))))))
				.then(Commands.literal("skin")
						.then(Commands.argument("who", StringArgumentType.word()).suggests(WHO)
								.then(Commands.argument("username", StringArgumentType.word())
										.executes(ctx -> FriendManager.skin(player(ctx), StringArgumentType.getString(ctx, "who"),
												StringArgumentType.getString(ctx, "username"))))))
				.then(Commands.literal("build")
						.then(Commands.argument("who", StringArgumentType.word()).suggests(WHO)
								.then(Commands.literal("house")
										.executes(ctx -> FriendManager.build(player(ctx), StringArgumentType.getString(ctx, "who"), "house", 0, 0)))
								.then(Commands.literal("wall")
										.executes(ctx -> FriendManager.build(player(ctx), StringArgumentType.getString(ctx, "who"), "wall", 0, 0))
										.then(Commands.argument("length", IntegerArgumentType.integer(1, FriendManager.MAX_WALL_LENGTH))
												.executes(ctx -> FriendManager.build(player(ctx), StringArgumentType.getString(ctx, "who"), "wall",
														IntegerArgumentType.getInteger(ctx, "length"), 0))
												.then(Commands.argument("height", IntegerArgumentType.integer(1, FriendManager.MAX_WALL_HEIGHT))
														.executes(ctx -> FriendManager.build(player(ctx), StringArgumentType.getString(ctx, "who"), "wall",
																IntegerArgumentType.getInteger(ctx, "length"), IntegerArgumentType.getInteger(ctx, "height"))))))
								.then(Commands.literal("farm")
										.executes(ctx -> FriendManager.build(player(ctx), StringArgumentType.getString(ctx, "who"), "farm", 1, 0))
										.then(Commands.argument("count", IntegerArgumentType.integer(1, FriendManager.MAX_FARMS))
												.executes(ctx -> FriendManager.build(player(ctx), StringArgumentType.getString(ctx, "who"), "farm",
														IntegerArgumentType.getInteger(ctx, "count"), 0))))))
				.then(Commands.literal("give")
						.then(Commands.argument("who", StringArgumentType.word()).suggests(WHO)
								.executes(ctx -> FriendManager.give(player(ctx), StringArgumentType.getString(ctx, "who")))))
				.then(Commands.argument("who", StringArgumentType.word()).suggests(WHO)
						.then(Commands.argument("order", StringArgumentType.word()).suggests(ORDERS)
								.executes(ctx -> FriendManager.order(player(ctx), StringArgumentType.getString(ctx, "who"),
										StringArgumentType.getString(ctx, "order"))))));
	}

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return ctx.getSource().getPlayerOrException();
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = player(ctx);
		List<FriendEntity> friends = FriendManager.friendsOf(player);
		if (friends.isEmpty()) {
			FriendManager.info(player, "No friends yet. Try /friend spawn Bob");
			return 0;
		}
		FriendManager.info(player, "You have " + friends.size() + " friend(s):");
		for (FriendEntity f : friends.subList(0, Math.min(friends.size(), 20))) {
			FriendManager.info(player, " - " + f.getFriendName()
					+ " (" + (int) Math.ceil(f.getHealth()) + " hp, " + f.getMode().name().toLowerCase(Locale.ROOT)
					+ (f.getSkinName().isEmpty() ? "" : ", skin: " + f.getSkinName()) + ")");
		}
		if (friends.size() > 20) FriendManager.info(player, " ...and " + (friends.size() - 20) + " more");
		return friends.size();
	}

	private static int help(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = player(ctx);
		FriendManager.info(p, "/friend spawn <name> [count] [skin username]");
		FriendManager.info(p, "/friend <name|squad|all> <follow|come|stay|guard|wander|attack|mine|farm|deliver|passive|aggressive|status|drop|dismiss>");
		FriendManager.info(p, "/friend build <who> wall [length] [height] | house | farm [count]");
		FriendManager.info(p, "Jobs: mine, farm, deliver (bring you their backpack). Give them blocks/seeds to build & plant.");
		FriendManager.info(p, "/friend give <who>  - gives them the item in your hand");
		FriendManager.info(p, "/friend skin <who> <username>");
		FriendManager.info(p, "/friend list");
		FriendManager.info(p, "Or just type in chat: 'bob follow me', 'everyone mine', 'squad build a wall 20 5', 'all build 3 farms'.");
		FriendManager.info(p, "Right-click a friend with an item to give it. Sneak + empty hand to take their gear back.");
		return 1;
	}
}
