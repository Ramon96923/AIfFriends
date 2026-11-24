package com.ramon.aifriends.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A list of block jobs shared by every friend of one owner, so several friends can split one build. */
public final class BuildJob {
	public enum Kind { CLEAR, SOLID, FARMLAND, WATER, SEED }

	public static final class Task {
		final BlockPos pos;
		final Kind kind;
		final BlockState creativeState;
		boolean done;
		@Nullable UUID claimedBy;

		Task(BlockPos pos, Kind kind, BlockState creativeState) {
			this.pos = pos;
			this.kind = kind;
			this.creativeState = creativeState;
		}
	}

	/** One active job per owner. */
	static final Map<UUID, BuildJob> JOBS = new HashMap<>();

	final String name;
	final List<Task> tasks = new ArrayList<>();
	final boolean farm;
	final BlockPos center;
	final int farmRadius;
	boolean announced;

	private BuildJob(String name, boolean farm, BlockPos center, int farmRadius) {
		this.name = name;
		this.farm = farm;
		this.center = center;
		this.farmRadius = farmRadius;
	}

	public static void start(UUID owner, BuildJob job) {
		JOBS.put(owner, job);
	}

	static @Nullable BuildJob get(@Nullable UUID owner) {
		return owner == null ? null : JOBS.get(owner);
	}

	public int size() {
		return tasks.size();
	}

	boolean isFinished() {
		for (Task t : tasks) if (!t.done) return false;
		return true;
	}

	/** Next unclaimed task, in build order (bottom layer first). */
	@Nullable Task claim(UUID friend) {
		for (Task t : tasks) {
			if (t.done) continue;
			if (t.claimedBy == null || t.claimedBy.equals(friend)) {
				t.claimedBy = friend;
				return t;
			}
		}
		return null;
	}

	void release(UUID friend) {
		for (Task t : tasks) {
			if (friend.equals(t.claimedBy) && !t.done) t.claimedBy = null;
		}
	}

	// ------------------------------------------------------------------ shapes
	// Local coordinates: x = to the owner's right, y = up, z = forward. Origin is 2 blocks in front of the owner's feet.

	private static BlockPos local(BlockPos origin, Direction forward, int x, int y, int z) {
		return origin.relative(forward.getClockWise(), x).relative(forward, z).above(y);
	}

	public static BuildJob wall(BlockPos feet, Direction forward, int length, int height) {
		BlockPos origin = feet.relative(forward, 2);
		BuildJob job = new BuildJob("wall", false, origin, 0);
		int start = -(length / 2);
		for (int y = 0; y < height; y++) {
			for (int x = start; x < start + length; x++) {
				job.add(local(origin, forward, x, y, 0), Kind.SOLID, Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		return job;
	}

	public static BuildJob house(BlockPos feet, Direction forward) {
		BlockPos origin = feet.relative(forward, 2);
		BuildJob job = new BuildJob("house", false, origin, 0);
		int half = 3, depth = 7, wallHeight = 4;
		// Clear the inside first.
		for (int y = 0; y < wallHeight; y++) {
			for (int z = 1; z < depth - 1; z++) {
				for (int x = -half + 1; x < half; x++) {
					job.add(local(origin, forward, x, y, z), Kind.CLEAR, Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int y = 0; y < wallHeight; y++) {
			for (int z = 0; z < depth; z++) {
				for (int x = -half; x <= half; x++) {
					boolean edge = x == -half || x == half || z == 0 || z == depth - 1;
					if (!edge) continue;
					boolean door = x == 0 && z == 0 && y <= 1;
					boolean window = y == 1 && (z == depth / 2 && (x == -half || x == half) || z == depth - 1 && x == 0);
					BlockPos pos = local(origin, forward, x, y, z);
					if (door || window) {
						job.add(pos, Kind.CLEAR, Blocks.AIR.defaultBlockState());
						continue;
					}
					boolean corner = (x == -half || x == half) && (z == 0 || z == depth - 1);
					job.add(pos, Kind.SOLID, corner ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
				}
			}
		}
		for (int z = 0; z < depth; z++) {
			for (int x = -half; x <= half; x++) {
				job.add(local(origin, forward, x, wallHeight, z), Kind.SOLID, Blocks.SPRUCE_PLANKS.defaultBlockState());
			}
		}
		return job;
	}

	/** {@code count} 9x9 farms side by side, each with a water block in the middle. */
	public static BuildJob farms(BlockPos feet, Direction forward, int count) {
		BlockPos origin = feet.relative(forward, 2);
		int span = count * 10 - 1;
		BlockPos center = local(origin, forward, (span / 2) - 4, 0, 4);
		BuildJob job = new BuildJob(count == 1 ? "farm" : count + " farms", true, center, Math.max(8, span / 2 + 3));
		List<BlockPos[]> cells = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			for (int z = 0; z < 9; z++) {
				for (int x = -4; x <= 4; x++) {
					boolean water = x == 0 && z == 4;
					BlockPos top = local(origin, forward, x + i * 10, 0, z);
					cells.add(new BlockPos[] {top, water ? BlockPos.ZERO : null});
				}
			}
		}
		for (BlockPos[] c : cells) job.add(c[0], Kind.CLEAR, Blocks.AIR.defaultBlockState());
		for (BlockPos[] c : cells) {
			if (c[1] != null) job.add(c[0].below(), Kind.WATER, Blocks.WATER.defaultBlockState());
			else job.add(c[0].below(), Kind.FARMLAND, Blocks.FARMLAND.defaultBlockState());
		}
		for (BlockPos[] c : cells) {
			if (c[1] == null) job.add(c[0], Kind.SEED, Blocks.WHEAT.defaultBlockState());
		}
		return job;
	}

	private void add(BlockPos pos, Kind kind, BlockState state) {
		tasks.add(new Task(pos, kind, state));
	}
}
