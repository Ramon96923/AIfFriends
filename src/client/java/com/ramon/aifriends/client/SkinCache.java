package com.ramon.aifriends.client;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.ramon.aifriends.AIFriends;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Looks up a Minecraft username's skin through Mojang's public API, downloads it and registers it as a texture.
 * Results are cached for the session; failures fall back to the default skin.
 */
public final class SkinCache {
	private SkinCache() {
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("aifriends");

	public record Skin(Identifier texture, boolean slim) {
	}

	private enum State { LOADING, FAILED }

	private static final Map<String, Skin> SKINS = new ConcurrentHashMap<>();
	private static final Map<String, State> STATES = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

	/** Returns the skin if it's ready, otherwise starts loading it and returns null for now. */
	public static @Nullable Skin get(String username) {
		if (username == null || username.isEmpty()) return null;
		String key = username.toLowerCase(Locale.ROOT);
		Skin skin = SKINS.get(key);
		if (skin != null) return skin;
		if (STATES.putIfAbsent(key, State.LOADING) == null) {
			CompletableFuture.runAsync(() -> download(key));
		}
		return null;
	}

	private static String httpGet(String url) throws Exception {
		HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode() + " for " + url);
		return res.body();
	}

	private static void download(String key) {
		try {
			JsonObject profile = JsonParser.parseString(httpGet("https://api.mojang.com/users/profiles/minecraft/" + key)).getAsJsonObject();
			String uuid = profile.get("id").getAsString();
			JsonObject session = JsonParser.parseString(httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)).getAsJsonObject();
			JsonArray props = session.getAsJsonArray("properties");
			String texturesB64 = null;
			for (int i = 0; i < props.size(); i++) {
				JsonObject p = props.get(i).getAsJsonObject();
				if ("textures".equals(p.get("name").getAsString())) texturesB64 = p.get("value").getAsString();
			}
			if (texturesB64 == null) throw new IllegalStateException("no textures");
			JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(texturesB64), StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("textures");
			if (textures == null || !textures.has("SKIN")) throw new IllegalStateException("player has no custom skin");
			JsonObject skinJson = textures.getAsJsonObject("SKIN");
			String url = skinJson.get("url").getAsString().replace("http://", "https://");
			boolean slim = skinJson.has("metadata") && skinJson.getAsJsonObject("metadata").has("model")
					&& "slim".equals(skinJson.getAsJsonObject("metadata").get("model").getAsString());

			HttpResponse<byte[]> img = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
					HttpResponse.BodyHandlers.ofByteArray());
			if (img.statusCode() != 200) throw new IllegalStateException("skin download HTTP " + img.statusCode());
			byte[] bytes = img.body();

			Minecraft.getInstance().execute(() -> {
				try {
					NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
					if (image.getHeight() == 32) image = SkinConverter.legacyToModern(image);
					Identifier id = Identifier.fromNamespaceAndPath(AIFriends.MOD_ID, "skins/" + key);
					DynamicTexture texture = new DynamicTexture(() -> "AI Friends skin " + key, image);
					Minecraft.getInstance().getTextureManager().register(id, texture);
					SKINS.put(key, new Skin(id, slim));
					STATES.remove(key);
				} catch (Exception e) {
					LOGGER.warn("Couldn't load skin for {}: {}", key, e.toString());
					STATES.put(key, State.FAILED);
				}
			});
		} catch (Exception e) {
			LOGGER.warn("Couldn't fetch skin for {}: {}", key, e.toString());
			STATES.put(key, State.FAILED);
		}
	}
}
