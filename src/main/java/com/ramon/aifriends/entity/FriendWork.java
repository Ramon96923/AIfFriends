package com.ramon.aifriends.entity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Mining, building and farming behaviour. Runs from {@link FriendEntity.WorkGoal}. */
final class FriendWork {
	private FriendWork() {
	}

	/** Which friend has claimed which ore / crop, so two friends never go for the same block. */
	static final Map<BlockPos, UUID> CLAIMS = new ConcurrentHashMap<>();

	static void releaseClaims(FriendEntity f) {
		CLAIMS.values().removeIf(id -> id.equals(f.getUUID()));
		BuildJob job = BuildJob.get(f.getOwnerId());
		if (job != null) job.release(f.getUUID());
		f.buildTask = null;
		stopBreaking(f);
		f.workTarget = null;
	}

	private static boolean claim(FriendEntity f, BlockPos pos) {
		UUID other = CLAIMS.get(pos);
		if (other != null && !other.equals(f.getUUID())) return false;
		CLAIMS.put(pos.immutable(), f.getUUID());
		return true;
	}

	// ------------------------------------------------------------------ block helpers

	static boolean isOre(BlockState state) {
		if (state.is(Blocks.ANCIENT_DEBRIS)) return true;
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.endsWith("_ore");
	}

	/** Natural blocks a friend is allowed to dig through or clear. Never player-made stuff like planks, chests, glass... */
	static boolean isNatural(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.DIRT)
				|| state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.SANDSTONE)
				|| state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK) || isOre(state);
	}

	private static boolean touchesFluid(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (!level.getFluidState(pos.relative(d)).isEmpty()) return true;
		}
		return false;
	}

	private static boolean canUseTool(ItemStack tool, BlockState state) {
		return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
	}

	private static int breakTicks(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0) return -1;
		float speed = Math.max(1.0f, tool.getDestroySpeed(state));
		float ticks = hardness * (canUseTool(tool, state) ? 30.0f : 100.0f) / speed;
		return Math.max(2, (int) Math.ceil(ticks));
	}

	static void stopBreakingPublic(FriendEntity f) {
		stopBreaking(f);
	}

	private static void stopBreaking(FriendEntity f) {
		if (f.breakPos != null && f.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(f.getId(), f.breakPos, -1);
		}
		f.breakPos = null;
		f.breakProgress = 0;
	}

	/** Works on breaking a block. Returns true once it's broken. Drops go into the friend's backpack. */
	private static boolean breakBlock(FriendEntity f, ServerLevel level, BlockPos pos, boolean keepDrops) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			stopBreaking(f);
			return true;
		}
		ItemStack tool = f.getMainHandItem();
		if (!pos.equals(f.breakPos)) {
			stopBreaking(f);
			f.breakPos = pos.immutable();
		}
		int needed = breakTicks(level, pos, state, tool);
		if (needed < 0) return false;
		f.breakProgress++;
		f.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		if (f.breakProgress % 5 == 1) f.swing(InteractionHand.MAIN_HAND);
		level.destroyBlockProgress(f.getId(), pos, Math.min(9, f.breakProgress * 10 / needed));
		if (f.breakProgress < needed) return false;

		List<ItemStack> drops = canUseTool(tool, state)
				? Block.getDrops(state, level, pos, level.getBlockEntity(pos), f, tool)
				: List.of();
		stopBreaking(f);
		level.destroyBlock(pos, false, f);
		if (!tool.isEmpty() && tool.isDamageableItem()) tool.hurtAndBreak(1, f, EquipmentSlot.MAINHAND);
		if (keepDrops) {
			for (ItemStack drop : drops) {
				ItemStack left = f.addToPack(drop);
				if (!left.isEmpty()) f.spawnAtLocation(level, left);
			}
		}
		return true;
	}

	private static boolean near(FriendEntity f, BlockPos pos, double reach) {
		return f.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach * reach;
	}

	private static void walkTo(FriendEntity f, BlockPos pos) {
		if (f.navRecalc-- > 0 && !f.getNavigation().isDone()) return;
		f.navRecalc = 15;
		f.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
	}

	private static void sayLimited(FriendEntity f, String event) {
		if (f.tickCount - f.lastNeedTick > 600) {
			f.lastNeedTick = f.tickCount;
			f.say(event);
		}
	}

	// ------------------------------------------------------------------ delivering

	/** Walks back to the owner and hands over the backpack. Returns true while busy delivering. */
	static boolean tickDeliver(FriendEntity f, ServerLevel level) {
		if (!f.delivering) return false;
		ServerPlayer owner = f.getOwner();
		if (owner == null || owner.level() != level) {
			f.delivering = false;
			return false;
		}
		if (f.distanceToSqr(owner) > 24 * 24) f.teleportNear(owner);
		if (f.distanceToSqr(owner) <= 3.0 * 3.0) {
			f.getNavigation().stop();
			f.say(f.handOverPack(owner) ? "deliver" : "deliver_nothing");
			f.delivering = false;
			return false;
		}
		if (f.navRecalc-- <= 0) {
			f.navRecalc = 10;
			f.getNavigation().moveTo(owner, 1.2);
		}
		return true;
	}

	// ------------------------------------------------------------------ mining

	static void tickMine(FriendEntity f, ServerLevel level) {
		if (f.packFull()) {
			f.say("pack_full");
			f.delivering = true;
			return;
		}
		BlockPos target = f.workTarget;
		if (target != null && !isOre(level.getBlockState(target))) {
			CLAIMS.remove(target);
			target = f.workTarget = null;
			f.stuckTicks = 0;
		}
		if (target == null) {
			if (f.tickCount % 20 != 0) return;
			target = findOre(f, level);
			if (target == null) {
				sayLimited(f, "no_ores");
				ServerPlayer owner = f.getOwner();
				if (owner != null && owner.level() == level && f.distanceToSqr(owner) > 8 * 8) f.getNavigation().moveTo(owner, 1.1);
				return;
			}
			f.workTarget = target;
			f.stuckTicks = 0;
			f.bestDist = Double.MAX_VALUE;
		}

		BlockState ore = level.getBlockState(target);
		if (!canUseTool(f.getMainHandItem(), ore)) {
			sayLimited(f, "need_pickaxe");
			giveUp(f, target);
			return;
		}
		if (touchesFluid(level, target)) {
			giveUp(f, target);
			return;
		}

		if (near(f, target, 3.5)) {
			breakBlock(f, level, target, true);
			return;
		}

		// Walk if we can, otherwise tunnel towards the ore.
		double dist = f.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		if (dist < f.bestDist - 0.5) {
			f.bestDist = dist;
			f.stuckTicks = 0;
		} else {
			f.stuckTicks++;
		}
		if (f.stuckTicks < 40) {
			walkTo(f, target);
			return;
		}
		tunnelTowards(f, level, target);
	}

	private static void giveUp(FriendEntity f, BlockPos target) {
		f.badBlocks.add(target.immutable());
		CLAIMS.remove(target);
		stopBreaking(f);
		f.workTarget = null;
	}

	private static void tunnelTowards(FriendEntity f, ServerLevel level, BlockPos target) {
		BlockPos feet = f.blockPosition();
		int dx = target.getX() - feet.getX(), dy = target.getY() - feet.getY(), dz = target.getZ() - feet.getZ();
		int sx = 0, sz = 0;
		if (Math.abs(dx) >= Math.abs(dz)) sx = Integer.signum(dx); else sz = Integer.signum(dz);
		int sy = dy < -1 ? -1 : (dy > 1 ? 1 : 0);
		if (sx == 0 && sz == 0) sx = 1;
		BlockPos step = feet.offset(sx, sy, sz);
		BlockPos[] toClear = sy > 0
				? new BlockPos[] {feet.above(2), step, step.above()}
				: new BlockPos[] {step, step.above()};
		for (BlockPos p : toClear) {
			BlockState s = level.getBlockState(p);
			if (s.isAir() || s.canBeReplaced()) continue;
			if (!isNatural(s) || touchesFluid(level, p) || s.getDestroySpeed(level, p) < 0) {
				giveUp(f, target);
				return;
			}
			breakBlock(f, level, p, true);
			return;
		}
		// Path is open: step into it.
		f.getMoveControl().setWantedPosition(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 1.0);
		if (sy > 0) f.getJumpControl().jump();
		f.stuckTicks = 30;
		f.bestDist = Double.MAX_VALUE;
	}

	private static @Nullable BlockPos findOre(FriendEntity f, ServerLevel level) {
		BlockPos origin = f.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = -12; y <= 6; y++) {
			for (int x = -12; x <= 12; x++) {
				for (int z = -12; z <= 12; z++) {
					p.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					double d = x * x + y * y * 1.5 + z * z;
					if (d >= bestDist) continue;
					if (!isOre(level.getBlockState(p))) continue;
					if (f.badBlocks.contains(p)) continue;
					UUID other = CLAIMS.get(p);
					if (other != null && !other.equals(f.getUUID())) continue;
					best = p.immutable();
					bestDist = d;
				}
			}
		}
		if (best != null) claim(f, best);
		return best;
	}

	// ------------------------------------------------------------------ building

	static void tickBuild(FriendEntity f, ServerLevel level) {
		BuildJob job = BuildJob.get(f.getOwnerId());
		if (job == null) {
			f.setMode(FriendEntity.Mode.FOLLOW);
			return;
		}
		BuildJob.Task task = f.buildTask;
		if (task == null || task.done) {
			task = f.buildTask = job.claim(f.getUUID());
			f.stuckTicks = 0;
			if (task == null) {
				if (job.isFinished()) finishJob(f, job);
				return;
			}
		}

		if (isTaskSatisfied(level, task)) {
			task.done = true;
			f.buildTask = null;
			return;
		}

		f.stuckTicks++;
		if (!near(f, task.pos, 4.0) && f.stuckTicks < 160) {
			walkTo(f, task.pos);
			return;
		}
		if (f.tickCount % 4 != 0) return; // place at a natural pace
		if (performTask(f, level, task)) {
			task.done = true;
			f.buildTask = null;
			f.swing(InteractionHand.MAIN_HAND);
		}
	}

	private static boolean isTaskSatisfied(ServerLevel level, BuildJob.Task task) {
		BlockState s = level.getBlockState(task.pos);
		return switch (task.kind) {
			case CLEAR -> s.isAir() || (!s.canBeReplaced() && !isNatural(s));
			case SOLID -> !s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty();
			case FARMLAND -> s.is(Blocks.FARMLAND);
			case WATER -> s.is(Blocks.WATER);
			case SEED -> s.getBlock() instanceof CropBlock || !level.getBlockState(task.pos.below()).is(Blocks.FARMLAND);
		};
	}

	private static boolean performTask(FriendEntity f, ServerLevel level, BuildJob.Task task) {
		ServerPlayer owner = f.getOwner();
		boolean creative = owner != null && owner.isCreative();
		BlockState current = level.getBlockState(task.pos);
		switch (task.kind) {
			case CLEAR -> {
				return breakBlock(f, level, task.pos, !current.canBeReplaced());
			}
			case SOLID -> {
				BlockState place;
				if (creative) {
					place = task.creativeState;
				} else {
					ItemStack blocks = f.takeFromPack(FriendWork::isBuildingBlock);
					if (blocks.isEmpty()) {
						sayLimited(f, "need_blocks");
						f.stuckTicks = 0;
						return false;
					}
					place = ((BlockItem) blocks.getItem()).getBlock().defaultBlockState();
				}
				if (!current.isAir() && current.canBeReplaced()) level.destroyBlock(task.pos, false, f);
				level.setBlock(task.pos, place, Block.UPDATE_ALL);
				return true;
			}
			case FARMLAND -> {
				BlockPos above = task.pos.above();
				BlockState aboveState = level.getBlockState(above);
				if (!aboveState.isAir() && aboveState.canBeReplaced()) level.destroyBlock(above, false, f);
				level.setBlock(task.pos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
				return true;
			}
			case WATER -> {
				level.setBlock(task.pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
				return true;
			}
			case SEED -> {
				BlockState crop;
				if (creative) {
					crop = task.creativeState;
				} else {
					ItemStack seed = f.takeFromPack(FriendWork::isSeed);
					if (seed.isEmpty()) {
						sayLimited(f, "need_seeds");
						return true; // leave the farmland empty, they can plant later
					}
					crop = ((BlockItem) seed.getItem()).getBlock().defaultBlockState();
				}
				if (!current.isAir() && current.canBeReplaced()) level.destroyBlock(task.pos, false, f);
				level.setBlock(task.pos, crop, Block.UPDATE_ALL);
				return true;
			}
		}
		return true;
	}

	private static void finishJob(FriendEntity f, BuildJob job) {
		if (!job.announced) {
			job.announced = true;
			f.say("build_done");
		}
		if (job.farm) {
			f.startFarming(job.center, job.farmRadius);
		} else {
			f.setMode(FriendEntity.Mode.FOLLOW);
		}
	}

	static boolean isBuildingBlock(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem bi)) return false;
		Block block = bi.getBlock();
		if (block instanceof CropBlock) return false;
		BlockState state = block.defaultBlockState();
		return state.isRedstoneConductor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) || block == Blocks.GLASS;
	}

	static boolean isSeed(ItemStack stack) {
		return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
	}

	// ------------------------------------------------------------------ farming

	static void tickFarm(FriendEntity f, ServerLevel level) {
		if (f.packFull()) {
			f.say("pack_full");
			f.delivering = true;
			return;
		}
		BlockPos anchor = f.anchor != null ? f.anchor : f.blockPosition();
		BlockPos target = f.workTarget;
		if (target != null && !isFarmWork(f, level, target)) {
			CLAIMS.remove(target);
			target = f.workTarget = null;
		}
		if (target == null) {
			if (f.tickCount % 20 != 0) return;
			target = findFarmWork(f, level, anchor);
			if (target == null) {
				if (!near(f, anchor, 4)) walkTo(f, anchor);
				return;
			}
			f.workTarget = target;
			f.stuckTicks = 0;
		}
		f.stuckTicks++;
		if (!near(f, target, 2.5) && f.stuckTicks < 200) {
			walkTo(f, target);
			return;
		}
		if (f.stuckTicks >= 200) {
			giveUp(f, target);
			return;
		}
		BlockState state = level.getBlockState(target);
		if (state.getBlock() instanceof CropBlock crop) {
			List<ItemStack> drops = Block.getDrops(state, level, target, null, f, ItemStack.EMPTY);
			level.destroyBlock(target, false, f);
			boolean replanted = false;
			for (ItemStack drop : drops) {
				if (!replanted && drop.is(crop.asItem())) {
					drop.shrink(1);
					replanted = true;
				}
				if (!drop.isEmpty()) {
					ItemStack left = f.addToPack(drop);
					if (!left.isEmpty()) f.spawnAtLocation(level, left);
				}
			}
			level.setBlock(target, crop.defaultBlockState(), Block.UPDATE_ALL);
		} else {
			ItemStack seed = f.takeFromPack(FriendWork::isSeed);
			if (!seed.isEmpty()) {
				level.setBlock(target, ((BlockItem) seed.getItem()).getBlock().defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		f.swing(InteractionHand.MAIN_HAND);
		CLAIMS.remove(target);
		f.workTarget = null;
	}

	private static boolean isFarmWork(FriendEntity f, ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
		return state.isAir() && level.getBlockState(pos.below()).is(Blocks.FARMLAND) && f.hasInPack(FriendWork::isSeed);
	}

	private static @Nullable BlockPos findFarmWork(FriendEntity f, ServerLevel level, BlockPos anchor) {
		int r = f.farmRadius;
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = -2; y <= 2; y++) {
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					p.set(anchor.getX() + x, anchor.getY() + y, anchor.getZ() + z);
					double d = f.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
					if (d >= bestDist || f.badBlocks.contains(p)) continue;
					UUID other = CLAIMS.get(p);
					if (other != null && !other.equals(f.getUUID())) continue;
					if (!isFarmWork(f, level, p)) continue;
					best = p.immutable();
					bestDist = d;
				}
			}
		}
		if (best != null) claim(f, best);
		return best;
	}
}
