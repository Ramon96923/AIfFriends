package com.ramon.aifriends;

import com.ramon.aifriends.command.FriendChatListener;
import com.ramon.aifriends.command.FriendCommands;
import com.ramon.aifriends.entity.FriendEntity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class AIFriends implements ModInitializer {
	public static final String MOD_ID = "aifriends";

	private static final ResourceKey<EntityType<?>> FRIEND_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "friend"));

	public static final EntityType<FriendEntity> FRIEND = FabricEntityType.Builder
			.createMob(FriendEntity::new, MobCategory.MISC, builder -> builder.defaultAttributes(FriendEntity::createAttributes))
			.sized(0.6f, 1.8f)
			.eyeHeight(1.62f)
			.clientTrackingRange(10)
			.build(FRIEND_KEY);

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, FRIEND_KEY, FRIEND);

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> FriendCommands.register(dispatcher));
		ServerMessageEvents.CHAT_MESSAGE.register(FriendChatListener::onChat);
	}
}
