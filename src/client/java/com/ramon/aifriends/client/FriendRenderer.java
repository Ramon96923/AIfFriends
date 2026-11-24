package com.ramon.aifriends.client;

import com.ramon.aifriends.entity.FriendEntity;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;

public class FriendRenderer extends HumanoidMobRenderer<FriendEntity, AvatarRenderState, PlayerModel> {
	private static final RenderStateDataKey<Identifier> TEXTURE = RenderStateDataKey.create(() -> "aifriends:texture");
	private static final RenderStateDataKey<Boolean> SLIM = RenderStateDataKey.create(() -> "aifriends:slim");

	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public FriendRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
		this.wideModel = this.model;
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new HumanoidArmorLayer<>(this,
				ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
				context.getEquipmentRenderer()));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(FriendEntity entity, AvatarRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		SkinCache.Skin skin = SkinCache.get(entity.getSkinName());
		if (skin != null) {
			state.setData(TEXTURE, skin.texture());
			state.setData(SLIM, skin.slim());
		} else {
			var fallback = DefaultPlayerSkin.get(entity.getUUID());
			state.setData(TEXTURE, fallback.body().texturePath());
			state.setData(SLIM, fallback.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM);
		}
		// Swap arm thickness to match the skin.
		this.model = Boolean.TRUE.equals(state.getData(SLIM)) ? slimModel : wideModel;
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return state.getData(TEXTURE);
	}
}
