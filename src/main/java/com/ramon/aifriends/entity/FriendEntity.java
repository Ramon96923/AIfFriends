package com.ramon.aifriends.entity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class FriendEntity extends PathfinderMob {
	/** Minecraft username whose skin this friend wears. Synced to clients. */
	private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(FriendEntity.class, EntityDataSerializers.STRING);

	/** Last time (game time) any friend of a given owner said something unprompted, so armies don't spam chat. */
	private static final Map<UUID, Long> LAST_CHATTER = new HashMap<>();

	public enum Mode { FOLLOW, STAY, GUARD, WANDER }

	private @Nullable UUID ownerId;
	private Mode mode = Mode.FOLLOW;
	private boolean aggressive = true;
	private String group = "";
	private @Nullable BlockPos anchor;
	private @Nullable LivingEntity forcedTarget;
	private @Nullable LivingEntity trackedTarget;
	private int hurtChatCooldown;
	private boolean saidLowHealth;
	private int idleChatTimer = 2400 + (int) (Math.random() * 3600);

	public FriendEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.setCanPickUpLoot(true);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			this.setDropChance(slot, 2.0f);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(SKIN, "");
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
		this.goalSelector.addGoal(2, new FollowOwnerGoal(this));
		this.goalSelector.addGoal(2, new ReturnToAnchorGoal(this));
		this.goalSelector.addGoal(5, new WanderGoal(this));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	// ---------------------------------------------------------------- accessors

	public String getSkinName() {
		return this.entityData.get(SKIN);
	}

	public void setSkinName(String name) {
		this.entityData.set(SKIN, name == null ? "" : name);
	}

	public @Nullable UUID getOwnerId() {
		return ownerId;
	}

	public void setOwner(ServerPlayer owner) {
		this.ownerId = owner.getUUID();
	}

	public boolean isOwnedBy(Player player) {
		return ownerId != null && ownerId.equals(player.getUUID());
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public Mode getMode() {
		return mode;
	}

	public boolean isAggressive() {
		return aggressive;
	}

	public String getFriendName() {
		Component name = this.getCustomName();
		return name == null ? "Friend" : name.getString();
	}

	public @Nullable ServerPlayer getOwner() {
		if (ownerId == null || !(this.level() instanceof ServerLevel serverLevel)) return null;
		return serverLevel.getServer().getPlayerList().getPlayer(ownerId);
	}

	private boolean isFriendly(@Nullable Entity entity) {
		if (entity == null) return false;
		if (ownerId != null && entity.getUUID().equals(ownerId)) return true;
		return entity instanceof FriendEntity other && other.ownerId != null && other.ownerId.equals(ownerId);
	}

	// ---------------------------------------------------------------- orders

	public void setMode(Mode mode) {
		this.mode = mode;
		this.forcedTarget = null;
		if (mode == Mode.STAY || mode == Mode.GUARD) {
			this.anchor = this.blockPosition();
		} else {
			this.anchor = null;
		}
		if (mode == Mode.STAY) {
			this.setTarget(null);
			this.getNavigation().stop();
		}
	}

	public void setAggressive(boolean aggressive) {
		this.aggressive = aggressive;
		if (!aggressive && this.getTarget() != null && this.getTarget() != this.getLastHurtByMob()) {
			this.setTarget(null);
		}
	}

	public void comeTo(ServerPlayer owner) {
		this.mode = Mode.FOLLOW;
		this.anchor = null;
		this.setTarget(null);
		this.forcedTarget = null;
		if (this.distanceToSqr(owner) > 48 * 48 || owner.level() != this.level()) {
			this.teleportNear(owner);
		} else {
			this.getNavigation().moveTo(owner, 1.4);
		}
	}

	public void attackTarget(LivingEntity target) {
		this.forcedTarget = target;
		this.setTarget(target);
	}

	/** Gives the friend an item. Returns the chat event to say. */
	public String giveItem(ServerPlayer owner, ItemStack stack) {
		EquipmentSlot slot = this.getEquipmentSlotForItem(stack);
		String event = "item";
		if (slot.isArmor()) {
			event = "item_armor";
		} else {
			boolean weapon = stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL);
			if (weapon) {
				slot = EquipmentSlot.MAINHAND;
				event = "item_weapon";
			} else if (slot == EquipmentSlot.MAINHAND && !this.getMainHandItem().isEmpty()) {
				slot = EquipmentSlot.OFFHAND;
			}
		}
		ItemStack old = this.getItemBySlot(slot);
		if (!old.isEmpty()) {
			returnToOwner(owner, old);
		}
		this.setItemSlot(slot, stack);
		this.setDropChance(slot, 2.0f);
		return event;
	}

	/** Hands every equipped item back to the owner. */
	public boolean returnAllItems(ServerPlayer owner) {
		boolean any = false;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = this.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				returnToOwner(owner, stack.copy());
				this.setItemSlot(slot, ItemStack.EMPTY);
				any = true;
			}
		}
		return any;
	}

	public void dropAllItems() {
		if (!(this.level() instanceof ServerLevel serverLevel)) return;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = this.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				this.spawnAtLocation(serverLevel, stack.copy());
				this.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
	}

	private static void returnToOwner(ServerPlayer owner, ItemStack stack) {
		if (!owner.getInventory().add(stack)) {
			owner.spawnAtLocation(owner.level(), stack);
		}
	}

	// ---------------------------------------------------------------- chat

	/** Sends a line to the owner as if this friend said it in chat. */
	public void say(String event) {
		ServerPlayer owner = this.getOwner();
		if (owner == null) return;
		String line = FriendChat.line(event, this.getRandom(), owner.getName().getString(), this.getFriendName(), (int) Math.ceil(this.getHealth()));
		owner.sendSystemMessage(Component.literal("<" + this.getFriendName() + "> ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
	}

	/** Like {@link #say} but rate-limited across all of this owner's friends. */
	private void chatter(String event, int minGapTicks) {
		if (ownerId == null) return;
		long now = this.level().getGameTime();
		Long last = LAST_CHATTER.get(ownerId);
		if (last != null && now - last < minGapTicks && now >= last) return;
		LAST_CHATTER.put(ownerId, now);
		this.say(event);
	}

	// ---------------------------------------------------------------- behaviour

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) return;

		if (hurtChatCooldown > 0) hurtChatCooldown--;

		// Slow natural regeneration when out of combat.
		if (this.tickCount % 40 == 0 && this.getHealth() < this.getMaxHealth()
				&& this.tickCount - this.getLastHurtByMobTimestamp() > 100) {
			this.heal(1.0f);
			if (this.getHealth() > this.getMaxHealth() * 0.5f) saidLowHealth = false;
		}

		ServerPlayer owner = this.getOwner();

		// Celebrate kills.
		if (trackedTarget != null && !trackedTarget.isAlive()) {
			if (trackedTarget.getLastHurtByMob() == this) {
				chatter("kill", 60);
			}
			trackedTarget = null;
		}
		if (this.getTarget() != null) trackedTarget = this.getTarget();

		// Never fight friends / owner.
		if (isFriendly(this.getTarget())) {
			this.setTarget(null);
		}
		if (forcedTarget != null && !forcedTarget.isAlive()) {
			forcedTarget = null;
		}

		if (this.tickCount % 10 == 0) {
			pickTarget(owner);
		}

		// Idle chatter.
		if (owner != null && this.getTarget() == null && --idleChatTimer <= 0) {
			idleChatTimer = 3600 + this.getRandom().nextInt(6000);
			if (this.distanceToSqr(owner) < 16 * 16) chatter("idle", 1200);
		}
	}

	private void pickTarget(@Nullable ServerPlayer owner) {
		if (forcedTarget != null) {
			if (this.getTarget() != forcedTarget) this.setTarget(forcedTarget);
			return;
		}
		LivingEntity current = this.getTarget();
		if (current != null && current.isAlive() && this.distanceToSqr(current) < 32 * 32) return;
		this.setTarget(null);

		if (mode == Mode.STAY) return; // only fights back when hit

		if (owner != null && owner.level() == this.level()) {
			// Defend the owner from whatever hurt them recently.
			LivingEntity attacker = owner.getLastHurtByMob();
			if (attacker != null && attacker.isAlive() && !isFriendly(attacker)
					&& owner.tickCount - owner.getLastHurtByMobTimestamp() < 200
					&& this.distanceToSqr(attacker) < 24 * 24) {
				this.setTarget(attacker);
				if (this.getRandom().nextInt(3) == 0) chatter("owner_hurt", 100);
				return;
			}
			if (aggressive) {
				// Help with whatever the owner is fighting.
				LivingEntity victim = owner.getLastHurtMob();
				if (victim != null && victim.isAlive() && !isFriendly(victim)
						&& owner.tickCount - owner.getLastHurtMobTimestamp() < 200
						&& this.distanceToSqr(victim) < 24 * 24) {
					this.setTarget(victim);
					return;
				}
			}
		}

		if (!aggressive || mode == Mode.WANDER && owner == null) return;

		// Look for nearby monsters (not neutral mobs, not creepers unless they're about to blow us up).
		double range = mode == Mode.GUARD ? 16.0 : 12.0;
		List<Mob> hostiles = this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(range), e ->
				e.isAlive() && e instanceof Enemy && !(e instanceof NeutralMob)
						&& (!(e instanceof Creeper) || e.getTarget() == this || (owner != null && e.getTarget() == owner))
						&& this.hasLineOfSight(e));
		Mob closest = null;
		double best = Double.MAX_VALUE;
		for (Mob m : hostiles) {
			double d = this.distanceToSqr(m);
			if (d < best) {
				best = d;
				closest = m;
			}
		}
		if (closest != null) this.setTarget(closest);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (isFriendly(attacker)) return false;
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			if (attacker instanceof LivingEntity living && !isFriendly(living) && living.isAlive()) {
				this.setTarget(living);
			}
			if (this.getHealth() < this.getMaxHealth() * 0.3f && !saidLowHealth && this.isAlive()) {
				saidLowHealth = true;
				this.say("low_health");
			} else if (hurtChatCooldown <= 0 && this.getRandom().nextInt(4) == 0) {
				hurtChatCooldown = 200;
				chatter("hurt", 60);
			}
		}
		return hurt;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !isFriendly(target) && super.canAttack(target);
	}

	@Override
	protected boolean considersEntityAsAlly(Entity other) {
		return isFriendly(other) || super.considersEntityAsAlly(other);
	}

	@Override
	public void die(DamageSource source) {
		if (!this.level().isClientSide()) this.say("death");
		super.die(source);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!isOwnedBy(player)) return super.mobInteract(player, hand);
		if (this.level().isClientSide()) return InteractionResult.SUCCESS;
		ServerPlayer owner = (ServerPlayer) player;
		ItemStack held = player.getItemInHand(hand);

		if (!held.isEmpty() && !player.isShiftKeyDown()) {
			ItemStack given = held.copy();
			held.setCount(0);
			this.say(this.giveItem(owner, given));
			return InteractionResult.SUCCESS;
		}
		if (held.isEmpty() && player.isShiftKeyDown()) {
			if (this.returnAllItems(owner)) this.say("take");
			return InteractionResult.SUCCESS;
		}
		if (held.isEmpty() && hand == InteractionHand.MAIN_HAND) {
			if (mode == Mode.FOLLOW) {
				this.setMode(Mode.STAY);
				this.say("stay");
			} else {
				this.setMode(Mode.FOLLOW);
				this.say("follow");
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	void teleportNear(ServerPlayer owner) {
		if (owner.level() != this.level()) return;
		double angle = this.getRandom().nextDouble() * Math.PI * 2;
		double x = owner.getX() + Math.cos(angle) * 1.5;
		double z = owner.getZ() + Math.sin(angle) * 1.5;
		this.getNavigation().stop();
		this.teleportTo(x, owner.getY(), z);
	}

	// ---------------------------------------------------------------- save / load

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (ownerId != null) output.store("FriendOwner", UUIDUtil.CODEC, ownerId);
		output.putString("FriendMode", mode.name());
		output.putBoolean("FriendAggressive", aggressive);
		output.putString("FriendGroup", group);
		output.putString("FriendSkin", getSkinName());
		if (anchor != null) output.store("FriendAnchor", BlockPos.CODEC, anchor);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		Optional<UUID> owner = input.read("FriendOwner", UUIDUtil.CODEC);
		this.ownerId = owner.orElse(null);
		try {
			this.mode = Mode.valueOf(input.getStringOr("FriendMode", "FOLLOW"));
		} catch (IllegalArgumentException e) {
			this.mode = Mode.FOLLOW;
		}
		this.aggressive = input.getBooleanOr("FriendAggressive", true);
		this.group = input.getStringOr("FriendGroup", "");
		this.setSkinName(input.getStringOr("FriendSkin", ""));
		this.anchor = input.read("FriendAnchor", BlockPos.CODEC).orElse(null);
	}

	// ---------------------------------------------------------------- goals

	static class FollowOwnerGoal extends Goal {
		private final FriendEntity friend;
		private int recalc;

		FollowOwnerGoal(FriendEntity friend) {
			this.friend = friend;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private @Nullable ServerPlayer owner() {
			ServerPlayer owner = friend.getOwner();
			return owner != null && owner.isAlive() && !owner.isSpectator() && owner.level() == friend.level() ? owner : null;
		}

		@Override
		public boolean canUse() {
			if (friend.mode != Mode.FOLLOW || friend.getTarget() != null) return false;
			ServerPlayer owner = owner();
			return owner != null && friend.distanceToSqr(owner) > 6 * 6;
		}

		@Override
		public boolean canContinueToUse() {
			if (friend.mode != Mode.FOLLOW || friend.getTarget() != null) return false;
			ServerPlayer owner = owner();
			return owner != null && friend.distanceToSqr(owner) > 3 * 3;
		}

		@Override
		public void start() {
			recalc = 0;
		}

		@Override
		public void stop() {
			friend.getNavigation().stop();
		}

		@Override
		public void tick() {
			ServerPlayer owner = owner();
			if (owner == null) return;
			friend.getLookControl().setLookAt(owner, 10.0f, (float) friend.getMaxHeadXRot());
			if (--recalc > 0) return;
			recalc = 10;
			if (friend.distanceToSqr(owner) > 24 * 24) {
				friend.teleportNear(owner);
				if (friend.getRandom().nextInt(4) == 0) friend.chatter("teleport", 200);
			} else {
				friend.getNavigation().moveTo(owner, friend.distanceToSqr(owner) > 12 * 12 ? 1.4 : 1.1);
			}
		}
	}

	static class ReturnToAnchorGoal extends Goal {
		private final FriendEntity friend;

		ReturnToAnchorGoal(FriendEntity friend) {
			this.friend = friend;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return (friend.mode == Mode.STAY || friend.mode == Mode.GUARD) && friend.anchor != null
					&& friend.getTarget() == null && friend.anchor.distToCenterSqr(friend.position()) > 2.5 * 2.5;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse() && !friend.getNavigation().isDone();
		}

		@Override
		public void start() {
			BlockPos a = friend.anchor;
			if (a != null) friend.getNavigation().moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 1.1);
		}
	}

	static class WanderGoal extends WaterAvoidingRandomStrollGoal {
		private final FriendEntity friend;

		WanderGoal(FriendEntity friend) {
			super(friend, 0.8);
			this.friend = friend;
		}

		@Override
		public boolean canUse() {
			return friend.mode == Mode.WANDER && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return friend.mode == Mode.WANDER && super.canContinueToUse();
		}
	}
}
