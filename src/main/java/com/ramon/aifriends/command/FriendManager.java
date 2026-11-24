package com.ramon.aifriends.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.ramon.aifriends.AIFriends;
import com.ramon.aifriends.entity.BuildJob;
import com.ramon.aifriends.entity.FriendEntity;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared logic used by both /friend commands and chat commands. */
public final class FriendManager {
	private FriendManager() {
	}

	public static final Set<String> ALL_WORDS = Set.of("all", "everyone", "everybody", "squad", "friends", "army", "guys", "team", "yall", "y'all", "troops", "boys", "gang");

	public static final List<String> ORDERS = List.of("follow", "come", "stay", "guard", "wander", "attack", "mine", "wood", "stone", "food", "farm", "craft", "deliver", "passive", "aggressive", "status", "drop", "dismiss");

	public static final List<String> STRUCTURES = List.of("wall", "house", "farm");

	public static final int MAX_SPAWN = 50;

	/** All friends owned by this player, in every dimension. */
	public static List<FriendEntity> friendsOf(ServerPlayer owner) {
		List<FriendEntity> list = new ArrayList<>();
		for (ServerLevel level : owner.level().getServer().getAllLevels()) {
			list.addAll(level.getEntities(AIFriends.FRIEND, f -> f.isAlive() && f.isOwnedBy(owner)));
		}
		return list;
	}

	/** Friends matching a selector: "all", a friend's exact name, or a squad (group) name. */
	public static List<FriendEntity> select(ServerPlayer owner, String who) {
		List<FriendEntity> all = friendsOf(owner);
		String w = who.toLowerCase(Locale.ROOT);
		if (ALL_WORDS.contains(w)) return all;
		List<FriendEntity> exact = new ArrayList<>();
		for (FriendEntity f : all) {
			if (f.getFriendName().equalsIgnoreCase(who)) exact.add(f);
		}
		if (!exact.isEmpty()) return exact;
		List<FriendEntity> group = new ArrayList<>();
		for (FriendEntity f : all) {
			if (f.getGroup().equalsIgnoreCase(who)) group.add(f);
		}
		return group;
	}

	/** Names that can be used as selectors (for tab completion and chat parsing). */
	public static Set<String> selectorNames(ServerPlayer owner) {
		Set<String> names = new LinkedHashSet<>();
		names.add("all");
		for (FriendEntity f : friendsOf(owner)) {
			names.add(f.getFriendName());
			if (!f.getGroup().isEmpty()) names.add(f.getGroup());
		}
		return names;
	}

	public static void info(ServerPlayer player, String text) {
		player.sendSystemMessage(Component.literal("[AI Friends] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(text).withStyle(ChatFormatting.GRAY)));
	}

	/** Lets up to two friends reply, then summarises the rest so big armies don't flood chat. */
	public static void replies(ServerPlayer owner, List<FriendEntity> friends, String event) {
		int shown = Math.min(2, friends.size());
		for (int i = 0; i < shown; i++) friends.get(i).say(event);
		if (friends.size() > shown) {
			info(owner, (friends.size() - shown) + " more friends nod.");
		}
	}

	/** Runs an order. Returns how many friends obeyed. */
	public static int order(ServerPlayer owner, String who, String orderWord) {
		List<FriendEntity> friends = select(owner, who);
		if (friends.isEmpty()) {
			info(owner, "You don't have any friends called '" + who + "'. Spawn some with /friend spawn <name>");
			return 0;
		}
		String order = orderWord.toLowerCase(Locale.ROOT);
		switch (order) {
			case "follow" -> {
				friends.forEach(f -> f.setMode(FriendEntity.Mode.FOLLOW));
				replies(owner, friends, "follow");
			}
			case "come" -> {
				friends.forEach(f -> f.comeTo(owner));
				replies(owner, friends, "come");
			}
			case "stay" -> {
				friends.forEach(f -> f.setMode(FriendEntity.Mode.STAY));
				replies(owner, friends, "stay");
			}
			case "guard" -> {
				friends.forEach(f -> f.setMode(FriendEntity.Mode.GUARD));
				replies(owner, friends, "guard");
			}
			case "wander" -> {
				friends.forEach(f -> f.setMode(FriendEntity.Mode.WANDER));
				replies(owner, friends, "wander");
			}
			case "mine" -> {
				friends.forEach(f -> f.setMode(FriendEntity.Mode.MINE));
				replies(owner, friends, "mine");
			}
			case "wood" -> {
				friends.forEach(f -> f.setMode(FriendEntity.Mode.GATHER_WOOD));
				replies(owner, friends, "wood");
			}
			case "stone" -> {
				friends.forEach(f -> f.setMode(FriendEntity.Mode.GATHER_STONE));
				replies(owner, friends, "stone");
			}
			case "food" -> {
				friends.forEach(f -> f.setMode(FriendEntity.Mode.GATHER_FOOD));
				replies(owner, friends, "food");
			}
			case "craft" -> {
				int shownCraft = 0;
				for (FriendEntity f : friends) {
					String made = f.craftNow();
					if (shownCraft < 3) {
						if (made != null) f.sayJob("crafted", made); else f.say("craft_nothing");
						shownCraft++;
					}
				}
			}
			case "farm" -> {
				friends.forEach(f -> f.startFarming(owner.blockPosition(), 10));
				replies(owner, friends, "farm");
			}
			case "deliver" -> {
				friends.forEach(FriendEntity::deliver);
				replies(owner, friends, "come");
			}
			case "passive" -> {
				friends.forEach(f -> f.setAggressive(false));
				replies(owner, friends, "passive");
			}
			case "aggressive" -> {
				friends.forEach(f -> f.setAggressive(true));
				replies(owner, friends, "aggressive");
			}
			case "attack" -> {
				LivingEntity target = lookTarget(owner);
				if (target == null) {
					friends.get(0).say("attack_none");
					return 0;
				}
				friends.forEach(f -> f.attackTarget(target));
				replies(owner, friends, "attack");
			}
			case "status" -> {
				for (FriendEntity f : friends.subList(0, Math.min(friends.size(), 8))) {
					f.say("status");
				}
				int carried = 0;
				for (FriendEntity f : friends) carried += f.packCount();
				info(owner, friends.size() + " friend(s), carrying " + carried + " items. Mode: " + friends.get(0).getMode().name().toLowerCase(Locale.ROOT)
						+ (friends.get(0).isAggressive() ? ", aggressive" : ", passive"));
			}
			case "drop" -> {
				friends.forEach(FriendEntity::dropAllItems);
				replies(owner, friends, "drop");
			}
			case "dismiss" -> {
				replies(owner, friends, "dismiss");
				friends.forEach(f -> {
					f.cleanup();
					f.dropAllItems();
					f.discard();
				});
			}
			default -> {
				info(owner, "Unknown order '" + orderWord + "'. Orders: " + String.join(", ", ORDERS));
				return 0;
			}
		}
		return friends.size();
	}

	public static final int MAX_WALL_LENGTH = 64, MAX_WALL_HEIGHT = 16, MAX_FARMS = 8;

	/** Starts a shared build job. a = wall length / farm count, b = wall height. 0 means default. */
	public static int build(ServerPlayer owner, String who, String structure, int a, int b) {
		List<FriendEntity> friends = select(owner, who);
		if (friends.isEmpty()) {
			info(owner, "You don't have any friends called '" + who + "'.");
			return 0;
		}
		Direction facing = owner.getDirection();
		BlockPos feet = owner.blockPosition();
		BuildJob job;
		String label;
		switch (structure.toLowerCase(Locale.ROOT)) {
			case "wall", "walls" -> {
				int length = a > 0 ? Math.min(a, MAX_WALL_LENGTH) : 7;
				int height = b > 0 ? Math.min(b, MAX_WALL_HEIGHT) : 3;
				job = BuildJob.wall(feet, facing, length, height);
				label = "wall " + length + " long and " + height + " high";
			}
			case "house", "hut", "home", "base" -> {
				job = BuildJob.house(feet, facing);
				label = "house";
			}
			case "farm", "farms", "field", "fields" -> {
				int count = a > 0 ? Math.min(a, MAX_FARMS) : 1;
				job = BuildJob.farms(feet, facing, count);
				label = count == 1 ? "farm" : count + " farms";
			}
			default -> {
				info(owner, "I can build: wall [length] [height], house, farm [count]");
				return 0;
			}
		}
		BuildJob.start(owner.getUUID(), job);
		friends.forEach(f -> f.setMode(FriendEntity.Mode.BUILD));
		int shown = Math.min(2, friends.size());
		for (int i = 0; i < shown; i++) friends.get(i).sayJob("build", label);
		info(owner, friends.size() + " friend(s) building a " + label + " (" + job.size() + " blocks)"
				+ (owner.isCreative() ? "." : ". They'll use blocks from their backpacks - give them some!"));
		return friends.size();
	}

	/** Gives the player's held item to the selected friends (split one each if several, whole stack if one). */
	public static int give(ServerPlayer owner, String who) {
		List<FriendEntity> friends = select(owner, who);
		if (friends.isEmpty()) {
			info(owner, "You don't have any friends called '" + who + "'.");
			return 0;
		}
		ItemStack held = owner.getMainHandItem();
		if (held.isEmpty()) {
			info(owner, "Hold the item you want to give in your main hand.");
			return 0;
		}
		if (friends.size() == 1) {
			FriendEntity f = friends.get(0);
			ItemStack stack = held.copy();
			held.setCount(0);
			f.say(f.giveItem(owner, stack));
			return 1;
		}
		int given = 0;
		String event = "item";
		for (FriendEntity f : friends) {
			if (held.isEmpty()) break;
			event = f.giveItem(owner, held.split(1));
			given++;
		}
		replies(owner, friends.subList(0, given), event);
		if (given < friends.size()) {
			info(owner, "Ran out of items. " + given + " of " + friends.size() + " friends got one.");
		}
		return given;
	}

	public static int skin(ServerPlayer owner, String who, String username) {
		if (!isValidUsername(username)) {
			info(owner, "'" + username + "' isn't a valid Minecraft username.");
			return 0;
		}
		List<FriendEntity> friends = select(owner, who);
		if (friends.isEmpty()) {
			info(owner, "You don't have any friends called '" + who + "'.");
			return 0;
		}
		friends.forEach(f -> f.setSkinName(username));
		replies(owner, friends, "skin");
		return friends.size();
	}

	public static boolean isValidUsername(String name) {
		return name.matches("[A-Za-z0-9_]{1,16}");
	}

	public static int spawn(ServerPlayer owner, String name, int count, @Nullable String skin) {
		if (skin != null && !isValidUsername(skin)) {
			info(owner, "'" + skin + "' isn't a valid Minecraft username.");
			return 0;
		}
		count = Math.max(1, Math.min(MAX_SPAWN, count));
		ServerLevel level = owner.level();
		List<FriendEntity> spawned = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			FriendEntity friend = new FriendEntity(AIFriends.FRIEND, level);
			String fullName = count == 1 ? name : name + (i + 1);
			friend.setCustomName(Component.literal(fullName));
			friend.setCustomNameVisible(true);
			friend.setGroup(name);
			friend.setOwner(owner);
			friend.setSkinName(skin != null ? skin : (isValidUsername(name) ? name : ""));
			double angle = (Math.PI * 2 * i) / count;
			double radius = count == 1 ? 1.5 : 1.5 + count / 8.0;
			double x = owner.getX() + Math.cos(angle) * radius;
			double z = owner.getZ() + Math.sin(angle) * radius;
			friend.setPos(x, owner.getY(), z);
			friend.setYRot(owner.getYRot());
			level.addFreshEntity(friend);
			spawned.add(friend);
		}
		replies(owner, spawned, "spawn");
		return spawned.size();
	}

	/** The living thing the player is looking at, up to 48 blocks away. */
	public static @Nullable LivingEntity lookTarget(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		double range = 48.0;
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !(e instanceof FriendEntity f && f.isOwnedBy(player)))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double dist = to.length();
			if (dist > range || dist < 0.01) continue;
			double dot = to.normalize().dot(look);
			double tolerance = Math.max(0.94, 1.0 - (e.getBbWidth() + 0.5) / dist);
			if (dot < tolerance) continue;
			if (!player.hasLineOfSight(e)) continue;
			double score = dist * (2.0 - dot);
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}
}
