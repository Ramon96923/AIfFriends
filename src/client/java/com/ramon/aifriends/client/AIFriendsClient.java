package com.ramon.aifriends.client;

import com.ramon.aifriends.AIFriends;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AIFriendsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(AIFriends.FRIEND, FriendRenderer::new);
	}
}
