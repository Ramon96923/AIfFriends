package com.ramon.aifriends.client;

import com.mojang.blaze3d.platform.NativeImage;

/** Turns very old 64x32 skins into the 64x64 layout the player model expects. */
final class SkinConverter {
	private SkinConverter() {
	}

	static NativeImage legacyToModern(NativeImage old) {
		NativeImage out = new NativeImage(64, 64, true);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 64; x++) {
				out.setPixel(x, y, old.getPixel(x, y));
			}
		}
		// Old skins have no separate left arm / left leg: reuse the right ones.
		copy(out, 0, 16, 16, 48, 16, 16);  // leg
		copy(out, 40, 16, 32, 48, 16, 16); // arm
		old.close();
		return out;
	}

	private static void copy(NativeImage img, int sx, int sy, int dx, int dy, int w, int h) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				img.setPixel(dx + x, dy + y, img.getPixel(sx + x, sy + y));
			}
		}
	}
}
