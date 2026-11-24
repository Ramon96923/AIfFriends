package com.ramon.aifriends.entity;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Gathering wood / stone / food, picking up drops, eating, and crafting + choosing tools. */
final class FriendGather {
	private FriendGather() {
	}

	// ================================================================== tools

	enum ToolType { PICKAXE, AXE, SWORD, SHOVEL }

	private static String path(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getPath();
	}

	static @Nullable ToolType typeOf(ItemStack stack) {
		if (stack.isEmpty()) return null;
		String p = path(stack.getItem());
		if (p.endsWith("_pickaxe")) return ToolType.PICKAXE;
		if (p.endsWith("_axe")) return ToolType.AXE;
		if (p.endsWith("_sword")) return ToolType.SWORD;
		if (p.endsWith("_shovel")) return ToolType.SHOVEL;
		return null;
	}

	/** 0 = not a tool, higher is better. */
	static int tier(ItemStack stack) {
		if (typeOf(stack) == null) return 0;
		String p = path(stack.getItem());
		if (p.startsWith("netherite")) return 6;
		if (p.startsWith("diamond")) return 5;
		if (p.startsWith("iron")) return 4;
		if (p.startsWith("copper")) return 3;
		if (p.startsWith("stone")) return 2;
		if (p.startsWith("golden")) return 2;
		if (p.startsWith("wooden")) return 1;
		return 1;
	}

	/** Which tool the friend should be holding right now. */
	private static ToolType wantedTool(FriendEntity f) {
		if (f.getTarget() != null) return ToolType.SWORD;
		return switch (f.getMode()) {
			case MINE, GATHER_STONE -> ToolType.PICKAXE;
			case GATHER_WOOD -> ToolType.AXE;
			default -> ToolType.SWORD;
		};
	}

	private static int score(ItemStack stack, ToolType wanted) {
		ToolType type = typeOf(stack);
		if (type == wanted) return 10 + tier(stack);
		// Axes are decent weapons too.
		if (wanted == ToolType.SWORD && type == ToolType.AXE) return 9 + tier(stack) - 1;
		if (wanted == ToolType.SWORD && stack.has(DataComponents.WEAPON)) return 9;
		return 0;
	}

	/** Swaps the best tool for the current job into the main hand. */
	static void chooseTool(FriendEntity f) {
		ToolType wanted = wantedTool(f);
		ItemStack hand = f.getMainHandItem();
		int bestScore = score(hand, wanted);
		int bestIndex = -1;
		for (int i = 0; i < f.pack.size(); i++) {
			int s = score(f.pack.get(i), wanted);
			if (s > bestScore) {
				bestScore = s;
				bestIndex = i;
			}
		}
		if (bestIndex < 0) return;
		ItemStack tool = f.pack.remove(bestIndex);
		if (!hand.isEmpty()) f.pack.add(hand.copy());
		f.setItemSlot(EquipmentSlot.MAINHAND, tool);
	}

	private static int bestTierOwned(FriendEntity f, ToolType type) {
		int best = typeOf(f.getMainHandItem()) == type ? tier(f.getMainHandItem()) : 0;
		for (ItemStack s : f.pack) if (typeOf(s) == type) best = Math.max(best, tier(s));
		return best;
	}

	// ------------------------------------------------------------------ crafting

	private static int count(FriendEntity f, java.util.function.Predicate<ItemStack> filter) {
		int n = 0;
		for (ItemStack s : f.pack) if (filter.test(s)) n += s.getCount();
		return n;
	}

	private static void remove(FriendEntity f, java.util.function.Predicate<ItemStack> filter, int amount) {
		for (int i = 0; i < f.pack.size() && amount > 0; i++) {
			ItemStack s = f.pack.get(i);
			if (!filter.test(s)) continue;
			int take = Math.min(amount, s.getCount());
			s.shrink(take);
			amount -= take;
		}
		f.pack.removeIf(ItemStack::isEmpty);
	}

	private static boolean isLog(ItemStack s) {
		return s.is(ItemTags.LOGS);
	}

	private static boolean isPlank(ItemStack s) {
		return s.is(ItemTags.PLANKS);
	}

	private static boolean isStick(ItemStack s) {
		return s.is(Items.STICK);
	}

	private static boolean isStoneMaterial(ItemStack s) {
		return s.is(Items.COBBLESTONE) || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.BLACKSTONE);
	}

	/** Planks we could get (planks + 4 per log). */
	private static int woodAvailable(FriendEntity f) {
		return count(f, FriendGather::isPlank) + 4 * count(f, FriendGather::isLog);
	}

	private static boolean usePlanks(FriendEntity f, int planks) {
		if (woodAvailable(f) < planks) return false;
		int have = count(f, FriendGather::isPlank);
		int fromPlanks = Math.min(have, planks);
		remove(f, FriendGather::isPlank, fromPlanks);
		int need = planks - fromPlanks;
		if (need > 0) {
			int logs = (need + 3) / 4;
			remove(f, FriendGather::isLog, logs);
			int leftover = logs * 4 - need;
			if (leftover > 0) f.addToPack(new ItemStack(Items.OAK_PLANKS, leftover));
		}
		return true;
	}

	/** Sticks available, converting planks if needed (2 planks = 4 sticks). */
	private static boolean useSticks(FriendEntity f, int sticks) {
		int have = count(f, FriendGather::isStick);
		if (have >= sticks) {
			remove(f, FriendGather::isStick, sticks);
			return true;
		}
		int missing = sticks - have;
		int planks = ((missing + 3) / 4) * 2;
		if (!usePlanks(f, planks)) return false;
		remove(f, FriendGather::isStick, have);
		int leftover = ((missing + 3) / 4) * 4 - missing;
		if (leftover > 0) f.addToPack(new ItemStack(Items.STICK, leftover));
		return true;
	}

	/** Smelts raw iron with coal "on the go" (1 coal = 8 iron). */
	private static void smelt(FriendEntity f) {
		int raw = count(f, s -> s.is(Items.RAW_IRON));
		int fuel = count(f, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL));
		if (raw == 0 || fuel == 0) return;
		int smelt = Math.min(raw, fuel * 8);
		int fuelUsed = (smelt + 7) / 8;
		remove(f, s -> s.is(Items.RAW_IRON), smelt);
		remove(f, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), fuelUsed);
		ItemStack left = f.addToPack(new ItemStack(Items.IRON_INGOT, smelt));
		if (!left.isEmpty() && f.level() instanceof ServerLevel level) f.spawnAtLocation(level, left);
	}

	private record Recipe(ToolType type, int tier, Item result, int material, int sticks) {
	}

	private static final List<Recipe> RECIPES = List.of(
			new Recipe(ToolType.PICKAXE, 5, Items.DIAMOND_PICKAXE, 3, 2), new Recipe(ToolType.AXE, 5, Items.DIAMOND_AXE, 3, 2), new Recipe(ToolType.SWORD, 5, Items.DIAMOND_SWORD, 2, 1),
			new Recipe(ToolType.PICKAXE, 4, Items.IRON_PICKAXE, 3, 2), new Recipe(ToolType.AXE, 4, Items.IRON_AXE, 3, 2), new Recipe(ToolType.SWORD, 4, Items.IRON_SWORD, 2, 1),
			new Recipe(ToolType.PICKAXE, 2, Items.STONE_PICKAXE, 3, 2), new Recipe(ToolType.AXE, 2, Items.STONE_AXE, 3, 2), new Recipe(ToolType.SWORD, 2, Items.STONE_SWORD, 2, 1),
			new Recipe(ToolType.PICKAXE, 1, Items.WOODEN_PICKAXE, 3, 2), new Recipe(ToolType.AXE, 1, Items.WOODEN_AXE, 3, 2), new Recipe(ToolType.SWORD, 1, Items.WOODEN_SWORD, 2, 1));

	private static int materialCount(FriendEntity f, int tier) {
		return switch (tier) {
			case 5 -> count(f, s -> s.is(Items.DIAMOND));
			case 4 -> count(f, s -> s.is(Items.IRON_INGOT));
			case 2 -> count(f, FriendGather::isStoneMaterial);
			default -> woodAvailable(f);
		};
	}

	private static void useMaterial(FriendEntity f, int tier, int amount) {
		switch (tier) {
			case 5 -> remove(f, s -> s.is(Items.DIAMOND), amount);
			case 4 -> remove(f, s -> s.is(Items.IRON_INGOT), amount);
			case 2 -> remove(f, FriendGather::isStoneMaterial, amount);
			default -> usePlanks(f, amount);
		}
	}

	/**
	 * Crafts the best pickaxe, axe and sword it can afford that beats what it already has.
	 * Returns the name of the best tool crafted, or null.
	 */
	static @Nullable String craftTools(FriendEntity f) {
		smelt(f);
		String crafted = null;
		for (ToolType type : new ToolType[] {ToolType.PICKAXE, ToolType.AXE, ToolType.SWORD}) {
			int owned = bestTierOwned(f, type);
			for (Recipe r : RECIPES) {
				if (r.type != type || r.tier <= owned) continue;
				int sticksHave = count(f, FriendGather::isStick);
				int stickPlanks = sticksHave >= r.sticks ? 0 : 2;
				boolean affordable = r.tier == 1
						? woodAvailable(f) >= r.material + stickPlanks
						: materialCount(f, r.tier) >= r.material && woodAvailable(f) >= stickPlanks;
				if (!affordable) continue;
				useMaterial(f, r.tier, r.material);
				useSticks(f, r.sticks);
				f.addToPack(new ItemStack(r.result));
				if (crafted == null) crafted = BuiltInRegistries.ITEM.getKey(r.result).getPath().replace('_', ' ');
				break;
			}
		}
		return crafted;
	}

	// ================================================================== upkeep (runs every second from FriendEntity.tick)

	static void upkeep(FriendEntity f, ServerLevel level) {
		if (f.isWorking() || f.getMode() == FriendEntity.Mode.FOLLOW) {
			String crafted = craftTools(f);
			if (crafted != null) f.sayJob("crafted", crafted);
		}
		chooseTool(f);
		eatIfHurt(f);
	}

	/** Picks up dropped items nearby while working (not what the owner just threw). */
	static void vacuum(FriendEntity f, ServerLevel level) {
		if (f.packFull()) return;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, f.getBoundingBox().inflate(3.0, 1.5, 3.0),
				e -> e.isAlive() && !e.hasPickUpDelay())) {
			ItemStack left = f.addToPack(item.getItem());
			if (left.isEmpty()) item.discard();
			else item.setItem(left);
		}
	}

	private static void eatIfHurt(FriendEntity f) {
		if (f.getHealth() > f.getMaxHealth() * 0.6f) return;
		for (int i = 0; i < f.pack.size(); i++) {
			ItemStack s = f.pack.get(i);
			FoodProperties food = s.get(DataComponents.FOOD);
			if (food == null || s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO)) continue;
			s.shrink(1);
			if (s.isEmpty()) f.pack.remove(i);
			f.heal(Math.max(2.0f, food.nutrition()));
			if (f.getRandom().nextInt(3) == 0) f.say("eat");
			return;
		}
	}

	// ================================================================== wood

	static void tickWood(FriendEntity f, ServerLevel level) {
		if (packCheck(f)) return;
		BlockPos target = f.workTarget;
		if (target != null && !level.getBlockState(target).is(BlockTags.LOGS)) {
			FriendWork.CLAIMS.remove(target);
			target = f.workTarget = null;
		}
		if (target == null) {
			if (f.tickCount % 20 != 0) return;
			target = find(f, level, 16, -3, 12, s -> s.is(BlockTags.LOGS));
			if (target == null) {
				noneFound(f, level, "no_trees");
				return;
			}
			f.workTarget = target;
			f.stuckTicks = 0;
		}
		f.stuckTicks++;
		if (!FriendWork.near(f, target, 4.5)) {
			if (f.stuckTicks > 300) {
				FriendWork.giveUp(f, target);
				return;
			}
			// Walk to the bottom of the tree.
			FriendWork.walkTo(f, groundBelow(level, target));
			return;
		}
		BlockState below = level.getBlockState(target.below());
		if (FriendWork.breakBlock(f, level, target, true)) {
			FriendWork.CLAIMS.remove(target);
			f.workTarget = null;
			// Replant if this was the bottom log.
			if (below.is(BlockTags.DIRT)) {
				ItemStack sapling = f.takeFromPack(s -> s.is(ItemTags.SAPLINGS));
				if (!sapling.isEmpty() && sapling.getItem() instanceof BlockItem bi) {
					level.setBlock(target, bi.getBlock().defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
	}

	private static BlockPos groundBelow(ServerLevel level, BlockPos pos) {
		BlockPos p = pos;
		for (int i = 0; i < 12 && level.getBlockState(p.below()).is(BlockTags.LOGS); i++) p = p.below();
		return p;
	}

	// ================================================================== stone

	static void tickStone(FriendEntity f, ServerLevel level) {
		if (packCheck(f)) return;
		ItemStack tool = f.getMainHandItem();
		if (typeOf(tool) != ToolType.PICKAXE) {
			FriendWork.sayLimited(f, "need_pickaxe_stone");
			return;
		}
		BlockPos target = f.workTarget;
		if (target != null && !isQuarryStone(level, target)) {
			FriendWork.CLAIMS.remove(target);
			target = f.workTarget = null;
		}
		if (target == null) {
			if (f.tickCount % 20 != 0) return;
			BlockPos feet = f.blockPosition();
			target = find(f, level, 10, -4, 4, null, p -> isQuarryStone(level, p) && !p.equals(feet.below()));
			if (target == null) {
				noneFound(f, level, "no_stone");
				return;
			}
			f.workTarget = target;
			f.stuckTicks = 0;
		}
		f.stuckTicks++;
		if (!FriendWork.near(f, target, 4.0)) {
			if (f.stuckTicks > 200) {
				FriendWork.giveUp(f, target);
				return;
			}
			FriendWork.walkTo(f, target);
			return;
		}
		if (FriendWork.breakBlock(f, level, target, true)) {
			FriendWork.CLAIMS.remove(target);
			f.workTarget = null;
		}
	}

	/** Natural stone that's exposed to air and not next to water/lava. */
	private static boolean isQuarryStone(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (!s.is(BlockTags.BASE_STONE_OVERWORLD)) return false;
		boolean exposed = false;
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			if (!level.getFluidState(n).isEmpty()) return false;
			if (level.getBlockState(n).isAir()) exposed = true;
		}
		return exposed;
	}

	// ================================================================== food

	static void tickFood(FriendEntity f, ServerLevel level) {
		if (packCheck(f)) return;
		if (f.tickCount % 20 != 0) return;
		Animal prey = null;
		double best = Double.MAX_VALUE;
		for (Animal a : level.getEntitiesOfClass(Animal.class, f.getBoundingBox().inflate(20, 6, 20), FriendGather::isFoodAnimal)) {
			// Leave at least two of each kind nearby so they can keep breeding.
			Class<?> kind = a.getClass();
			int sameKind = level.getEntitiesOfClass(Animal.class, a.getBoundingBox().inflate(16), o -> o.getClass() == kind && o.isAlive()).size();
			if (sameKind <= 2) continue;
			UUID claimed = FriendWork.CLAIMS.get(a.blockPosition());
			if (claimed != null && !claimed.equals(f.getUUID())) continue;
			double d = f.distanceToSqr(a);
			if (d < best) {
				best = d;
				prey = a;
			}
		}
		if (prey == null) {
			noneFound(f, level, "no_animals");
			return;
		}
		f.attackTarget(prey);
	}

	private static boolean isFoodAnimal(Animal a) {
		return a.isAlive() && !a.isBaby() && !a.isLeashed()
				&& (a instanceof Cow || a instanceof Pig || a instanceof Chicken || a instanceof Sheep || a instanceof Rabbit);
	}

	// ================================================================== shared

	/** Returns true (and starts delivering) when the backpack is full. */
	private static boolean packCheck(FriendEntity f) {
		if (!f.packFull()) return false;
		f.say("pack_full");
		f.delivering = true;
		return true;
	}

	private static void noneFound(FriendEntity f, ServerLevel level, String event) {
		FriendWork.sayLimited(f, event);
		ServerPlayer owner = f.getOwner();
		if (owner != null && owner.level() == level && f.distanceToSqr(owner) > 8 * 8) f.getNavigation().moveTo(owner, 1.1);
	}

	private static @Nullable BlockPos find(FriendEntity f, ServerLevel level, int r, int down, int up,
			java.util.function.Predicate<BlockState> stateTest) {
		return find(f, level, r, down, up, stateTest, null);
	}

	private static @Nullable BlockPos find(FriendEntity f, ServerLevel level, int r, int down, int up,
			java.util.function.@Nullable Predicate<BlockState> stateTest, java.util.function.@Nullable Predicate<BlockPos> posTest) {
		BlockPos origin = f.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = down; y <= up; y++) {
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					double d = x * x + y * y * 2.0 + z * z;
					if (d >= bestDist) continue;
					p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					if (f.badBlocks.contains(p)) continue;
					UUID other = FriendWork.CLAIMS.get(p);
					if (other != null && !other.equals(f.getUUID())) continue;
					if (stateTest != null && !stateTest.test(level.getBlockState(p))) continue;
					if (posTest != null && !posTest.test(p)) continue;
					best = p.immutable();
					bestDist = d;
				}
			}
		}
		if (best != null) FriendWork.CLAIMS.put(best, f.getUUID());
		return best;
	}
}
