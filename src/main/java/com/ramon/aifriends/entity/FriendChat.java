package com.ramon.aifriends.entity;

import java.util.List;
import java.util.Map;

import net.minecraft.util.RandomSource;

/**
 * Canned chat lines (no generative AI). Each event has a pool of lines; one is picked at random.
 * {owner} is replaced with the owner's name, {name} with the friend's name, {hp} with current health.
 */
public final class FriendChat {
	private FriendChat() {
	}

	private static final Map<String, List<String>> LINES = Map.ofEntries(
			Map.entry("spawn", List.of("Reporting for duty, {owner}!", "Hey {owner}! What's the plan?", "{name} ready to go!", "Let's do this!", "I'm here, boss.")),
			Map.entry("follow", List.of("Right behind you!", "On your six.", "Following, boss.", "Lead the way, {owner}!", "Where you go, I go.")),
			Map.entry("come", List.of("On my way!", "Coming, {owner}!", "Be right there!", "Running over!")),
			Map.entry("stay", List.of("Holding position.", "I'll wait here.", "Not moving an inch.", "Staying put, boss.")),
			Map.entry("guard", List.of("Nothing gets past me.", "Guarding this spot.", "I've got this area covered.", "Standing watch.")),
			Map.entry("wander", List.of("I'll stretch my legs a bit.", "Gonna look around.", "Free time? Sweet!", "I'll be around.")),
			Map.entry("attack", List.of("Taking it down!", "Charge!", "On it!", "It's going down!", "Engaging!")),
			Map.entry("attack_none", List.of("Attack what? I don't see anything.", "Point me at something first!", "There's nothing there, boss.")),
			Map.entry("passive", List.of("Okay, no fighting unless I have to.", "Peace mode. Got it.", "I'll keep my weapon down.")),
			Map.entry("aggressive", List.of("Weapons free!", "I'll take out any monster I see.", "Time to hunt.", "Let 'em come!")),
			Map.entry("dismiss", List.of("See ya, {owner}!", "Catch you later!", "Peace out!", "Call me if you need me.")),
			Map.entry("drop", List.of("Here's all my stuff.", "Dropping my gear.", "Take it, it's yours.")),
			Map.entry("item", List.of("Ooh, thanks!", "Nice, I'll use this!", "Sweet gear!", "Thanks, {owner}!", "This'll come in handy.")),
			Map.entry("item_weapon", List.of("Now we're talking!", "This thing looks sharp!", "Oh I'm gonna wreck stuff with this.", "Thanks for the upgrade!")),
			Map.entry("item_armor", List.of("Looking good, feeling tougher!", "Armor up!", "Thanks, I feel safer already.", "How do I look?")),
			Map.entry("take", List.of("Here, take it back.", "All yours.", "Handing it over.")),
			Map.entry("hurt", List.of("Ow!", "Hey, watch it!", "That hurt!", "Ouch!", "I'm taking hits!")),
			Map.entry("low_health", List.of("I'm hurt bad, {owner}!", "I need a break...", "Low health! Help!", "Not looking good here...")),
			Map.entry("kill", List.of("Got it!", "Target down!", "Too easy.", "Another one bites the dust.", "Did you see that?!", "Next!")),
			Map.entry("death", List.of("I'm down! Avenge me, {owner}!", "Tell my story...", "Ugh... I'm out.")),
			Map.entry("owner_hurt", List.of("Hey! Leave {owner} alone!", "Nobody hurts my boss!", "I got you, {owner}!")),
			Map.entry("hello", List.of("Hey {owner}!", "Yo!", "Hi there!", "What's up, {owner}?", "Hello!")),
			Map.entry("status", List.of("I'm at {hp} health.", "Feeling okay. {hp} health.", "{hp} health and ready.")),
			Map.entry("thanks", List.of("Anytime!", "No problem!", "That's what friends are for.", "You got it!")),
			Map.entry("praise", List.of("Thanks, boss!", "I try my best!", "Aw, you're making me blush.", "Team effort!")),
			Map.entry("confused", List.of("Huh?", "Sorry, didn't get that.", "Come again?", "I only know commands like follow, stay, guard or attack.")),
			Map.entry("skin", List.of("New look, who dis?", "Fresh fit!", "Do I look different?")),
			Map.entry("idle", List.of("Nice day, huh?", "Got any tasks for me?", "I'm bored. Can we fight something?", "Love the scenery around here.", "You're a great leader, {owner}.", "Anyone else hungry?", "I heard something over there...")),
			Map.entry("mine", List.of("Time to dig!", "I'll find some ores.", "Pickaxe ready!", "Diamonds, here I come.")),
			Map.entry("no_ores", List.of("I can't find any ores around here.", "No ores nearby. Take me somewhere else?", "Nothing to mine here, boss.")),
			Map.entry("need_pickaxe", List.of("I need a better pickaxe for this ore.", "My tool can't mine that. Got a pickaxe?", "Can't break that without the right pickaxe.")),
			Map.entry("build", List.of("Let's build it!", "Construction time!", "I'll get started.", "One {job}, coming up!")),
			Map.entry("build_done", List.of("Done building!", "Finished! Looks great, right?", "All built, boss!")),
			Map.entry("need_blocks", List.of("I'm out of blocks! Give me some.", "Need more blocks to keep building.", "Can someone pass me some blocks?")),
			Map.entry("need_seeds", List.of("I don't have any seeds to plant.", "Give me seeds and I'll plant them.")),
			Map.entry("farm", List.of("I'll take care of the crops.", "Farming time!", "Harvest and replant, got it.")),
			Map.entry("deliver", List.of("Here's everything I collected!", "Special delivery!", "Brought you the goods.")),
			Map.entry("deliver_nothing", List.of("My backpack's empty.", "I don't have anything for you yet.")),
			Map.entry("pack_full", List.of("My backpack is full! Bringing it to you.", "Can't carry any more, coming back.")),
			Map.entry("item_pack", List.of("Into the backpack it goes.", "I'll hold onto that.", "Got it, thanks!")),
			Map.entry("wood", List.of("Timber!", "I'll chop some trees.", "Lumberjack mode on.", "Going to get wood!")),
			Map.entry("stone", List.of("Off to the quarry!", "I'll get some cobble.", "Rocks, here I come.")),
			Map.entry("food", List.of("Going hunting!", "I'll bring back dinner.", "Time to find some food.")),
			Map.entry("no_trees", List.of("There aren't any trees around here.", "No trees nearby, boss.", "Can't find any wood here.")),
			Map.entry("no_stone", List.of("I can't find any stone I can reach.", "No exposed stone around here.")),
			Map.entry("no_animals", List.of("No animals to hunt nearby. I'm leaving at least two of each so they can breed.", "Nothing to hunt around here.")),
			Map.entry("need_pickaxe_stone", List.of("I need a pickaxe for stone. Get me some wood and I'll make one!", "No pickaxe! Give me one or let me chop wood first.")),
			Map.entry("crafted", List.of("Crafted a {job}!", "Made myself a {job}.", "Check it out, a new {job}!", "Upgraded to a {job}.")),
			Map.entry("craft_nothing", List.of("I can't make anything better right now.", "Not enough materials for an upgrade.", "My tools are as good as I can make them.")),
			Map.entry("eat", List.of("*munch munch*", "That hit the spot.", "Snack break!", "Yum.")),
			Map.entry("brawl", List.of("Sorry buddy, it's every friend for themselves!", "Fight me!", "Nothing personal!", "Let's settle this!", "You're going down!")),
			Map.entry("war", List.of("For my team!", "Charge!", "We've got this, team!", "Let's show them!")),
			Map.entry("tap_out", List.of("Okay okay, I give up!", "I yield! I yield!", "Ow, you win this one.", "Tapping out!")),
			Map.entry("victory", List.of("I'm the champion!", "Undefeated!", "Who's next?!", "GG everyone!", "Victory!")),
			Map.entry("ceasefire", List.of("Truce!", "Okay, we're cool.", "Good fight, everyone.", "Friends again!")),
			Map.entry("teleport", List.of("Wait up!", "Hey, don't leave me behind!", "Caught up!"))
	);

	public static String line(String event, RandomSource random, String owner, String name, int hp) {
		return line(event, random, owner, name, hp, "");
	}

	public static String line(String event, RandomSource random, String owner, String name, int hp, String job) {
		List<String> pool = LINES.getOrDefault(event, LINES.get("confused"));
		String line = pool.get(random.nextInt(pool.size()));
		return line.replace("{owner}", owner).replace("{name}", name).replace("{hp}", Integer.toString(hp)).replace("{job}", job.isEmpty() ? "build" : job);
	}
}
