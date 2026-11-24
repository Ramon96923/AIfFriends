package com.ramon.aifriends.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ramon.aifriends.entity.FriendEntity;
import com.ramon.aifriends.entity.FriendTalk;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets you talk to your friends in normal chat. Not generative: it looks for a friend/squad name
 * (or "everyone", "squad", ...) followed by a keyword it knows.
 * Examples: "bob follow me", "everyone attack!", "squad guard here", "hi guys", "thanks bob".
 */
public final class FriendChatListener {
	private FriendChatListener() {
	}

	private static final Map<String, String> ORDER_WORDS = Map.ofEntries(
			Map.entry("follow", "follow"), Map.entry("followme", "follow"), Map.entry("heel", "follow"), Map.entry("lets", "follow"), Map.entry("move", "follow"),
			Map.entry("come", "come"), Map.entry("here", "come"), Map.entry("tp", "come"), Map.entry("regroup", "come"),
			Map.entry("stay", "stay"), Map.entry("wait", "stay"), Map.entry("stop", "stay"), Map.entry("halt", "stay"), Map.entry("sit", "stay"), Map.entry("freeze", "stay"),
			Map.entry("guard", "guard"), Map.entry("defend", "guard"), Map.entry("protect", "guard"), Map.entry("watch", "guard"),
			Map.entry("wander", "wander"), Map.entry("explore", "wander"), Map.entry("roam", "wander"), Map.entry("free", "wander"),
			Map.entry("attack", "attack"), Map.entry("kill", "attack"), Map.entry("charge", "attack"), Map.entry("fight", "attack"), Map.entry("sic", "attack"),
			Map.entry("passive", "passive"), Map.entry("peace", "passive"), Map.entry("chill", "passive"), Map.entry("calm", "passive"),
			Map.entry("aggressive", "aggressive"), Map.entry("hostile", "aggressive"), Map.entry("weapons", "aggressive"),
			Map.entry("status", "status"), Map.entry("report", "status"), Map.entry("health", "status"), Map.entry("hp", "status"),
			Map.entry("drop", "drop"),
			Map.entry("mine", "mine"), Map.entry("dig", "mine"), Map.entry("mining", "mine"), Map.entry("ores", "mine"),
			Map.entry("wood", "wood"), Map.entry("chop", "wood"), Map.entry("lumber", "wood"), Map.entry("trees", "wood"), Map.entry("logs", "wood"), Map.entry("timber", "wood"),
			Map.entry("stone", "stone"), Map.entry("cobble", "stone"), Map.entry("cobblestone", "stone"), Map.entry("quarry", "stone"), Map.entry("rocks", "stone"),
			Map.entry("food", "food"), Map.entry("hunt", "food"), Map.entry("meat", "food"), Map.entry("hunting", "food"),
			Map.entry("craft", "craft"), Map.entry("tools", "craft"), Map.entry("upgrade", "craft"),
			Map.entry("farm", "farm"), Map.entry("harvest", "farm"), Map.entry("farming", "farm"), Map.entry("crops", "farm"),
			Map.entry("deliver", "deliver"), Map.entry("bring", "deliver"), Map.entry("loot", "deliver"),
			Map.entry("dismiss", "dismiss"), Map.entry("leave", "dismiss"), Map.entry("dismissed", "dismiss")
	);

	private static final Set<String> QUESTION_WORDS = Set.of("what", "whats", "how", "hows", "why", "who", "where", "when", "do", "does", "did",
			"are", "is", "can", "could", "would", "will", "have", "wanna", "u", "you", "r");

	public static void onChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound bound) {
		String text = message.signedContent();
		if (text == null || text.isBlank()) return;
		// Reply after the player's own message shows up.
		sender.level().getServer().execute(() -> handle(sender, text));
	}

	private static void handle(ServerPlayer sender, String text) {
		List<FriendEntity> mine = FriendManager.friendsOf(sender);
		if (mine.isEmpty()) return;

		String[] words = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_' ]", " ").trim().split("\\s+");
		if (words.length == 0) return;

		// Find who is being addressed: the first word that's "everyone"/"squad"/... or one of our friend/squad names.
		String who = null;
		int whoIndex = -1;
		for (int i = 0; i < words.length && who == null; i++) {
			String w = words[i];
			if (FriendManager.ALL_WORDS.contains(w)) {
				who = "all";
				whoIndex = i;
				break;
			}
			for (FriendEntity f : mine) {
				if (f.getFriendName().equalsIgnoreCase(w) || f.getGroup().equalsIgnoreCase(w)) {
					who = w;
					whoIndex = i;
					break;
				}
			}
		}
		long now = sender.level().getGameTime();
		String cleaned = String.join(" ", words);
		if (who == null) {
			// Not addressed by name: maybe it's an answer to something a friend just asked.
			UUID waiting = FriendTalk.waitingFriend(sender.getUUID(), now);
			if (waiting == null) return;
			for (FriendEntity f : mine) {
				if (f.getUUID().equals(waiting)) {
					String answer = FriendTalk.answer(sender.getUUID(), cleaned, f.getRandom());
					if (answer != null) f.sayRaw(answer);
					break;
				}
			}
			return;
		}
		boolean question = text.contains("?") || QUESTION_WORDS.contains(words[whoIndex == 0 && words.length > 1 ? 1 : 0]);

		// Building: "squad build a wall 20 5", "everyone build 3 farms", "bob build house".
		boolean wantsBuild = false;
		String structure = null;
		int[] numbers = new int[2];
		int found = 0;
		for (int i = 0; i < words.length; i++) {
			if (i == whoIndex) continue;
			String w = words[i];
			if (w.equals("build") || w.equals("make") || w.equals("construct")) wantsBuild = true;
			if (structure == null && List.of("wall", "walls", "house", "hut", "home", "base", "farm", "farms", "field", "fields").contains(w)) structure = w;
			if (found < 2 && w.matches("\\d{1,3}")) numbers[found++] = Integer.parseInt(w);
		}
		if (wantsBuild && structure != null) {
			FriendManager.build(sender, who, structure, numbers[0], numbers[1]);
			return;
		}
		if (wantsBuild) {
			FriendManager.info(sender, "Build what? Try: wall [length] [height], house, or farm [count]");
			return;
		}

		// Look for an order keyword anywhere in the message (excluding the name itself).
		String order = null;
		for (int i = 0; i < words.length; i++) {
			if (i == whoIndex) continue;
			if (order == null && ORDER_WORDS.containsKey(words[i])) order = ORDER_WORDS.get(words[i]);
		}
		// Questions like "bob do you like mining?" are small talk, not orders.
		boolean smallTalk = FriendTalk.matchesAnything(cleaned);
		if (order != null && !(question && smallTalk)) {
			FriendManager.order(sender, who, order);
			return;
		}
		List<FriendEntity> targets = FriendManager.select(sender, who);
		if (targets.isEmpty()) return;

		// An answer to a question this friend asked?
		UUID waiting = FriendTalk.waitingFriend(sender.getUUID(), now);
		if (!smallTalk && waiting != null) {
			for (FriendEntity f : targets) {
				if (f.getUUID().equals(waiting)) {
					String answer = FriendTalk.answer(sender.getUUID(), cleaned, f.getRandom());
					if (answer != null) {
						f.sayRaw(answer);
						return;
					}
				}
			}
		}

		int replied = 0;
		for (FriendEntity f : targets) {
			if (replied >= 2) break;
			String line = FriendTalk.reply(f, cleaned, f.getRandom(), sender.getName().getString(), now);
			if (line == null) {
				f.say("confused");
			} else {
				f.sayRaw(line);
			}
			replied++;
		}
	}
}
