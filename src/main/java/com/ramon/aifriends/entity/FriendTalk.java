package com.ramon.aifriends.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.RandomSource;

/**
 * Small talk. Not generative AI: a list of patterns, each with a pool of replies.
 * A reply can ask the player something back (a "topic"); the player's next message is then
 * treated as the answer, even without saying the friend's name.
 */
public final class FriendTalk {
	private FriendTalk() {
	}

	/** A reply line plus an optional follow-up topic the friend is now waiting on. */
	private record Line(String text, @Nullable String topic) {
	}

	private record Rule(Pattern pattern, List<Line> replies) {
	}

	private static Line l(String text) {
		return new Line(text, null);
	}

	private static Line ask(String text, String topic) {
		return new Line(text, topic);
	}

	private static Rule rule(String regex, Line... replies) {
		return new Rule(Pattern.compile("\\b(?:" + regex + ")\\b"), List.of(replies));
	}

	// ---------------------------------------------------------------- things the player can say to a friend

	private static final List<Rule> RULES = List.of(
			rule("how (was|is|has) (your|ur) day|hows? (your|ur) day|how(s| is) (it|life) going|how have you been|how(re| are) things",
					ask("Pretty good! I fought a zombie earlier and won. How about yours?", "day"),
					ask("Honestly? Great. I got to hang out with you. How was yours?", "day"),
					ask("Not bad, a bit boring until you showed up. Yours?", "day"),
					ask("Busy! Lots of walking around. How's your day going?", "day")),
			rule("how are (you|u|ya)|how r u|hru|how you doing|how ya doing|you (ok|okay|good|alright)|are you (ok|okay|good|alright)",
					ask("I'm doing good, thanks for asking! You?", "feeling"),
					ask("Never better! How about you?", "feeling"),
					ask("Pretty good. A little hungry. How are you?", "feeling"),
					ask("Living the dream. What about you, {owner}?", "feeling")),
			rule("what(s| is) up|wassup|whats good|sup",
					ask("Not much, just hanging out. What's up with you?", "feeling"),
					l("The sky, mostly."),
					ask("Just guarding the squad. You?", "feeling")),
			rule("what are you doing|what(re| are) you up to|wyd|what you doing",
					l("Just keeping an eye out for mobs."),
					l("Waiting for orders, boss."),
					l("Thinking about diamonds. As usual."),
					ask("Counting blocks. Want to do something fun?", "yesno_fun")),
			rule("good morning|morning",
					l("Morning, {owner}! Sleep well?"),
					l("Good morning! Ready for an adventure?"),
					l("Rise and shine!")),
			rule("good night|goodnight|gn|night night|going to (bed|sleep)",
					l("Night, {owner}! I'll keep watch."),
					l("Sleep well! Don't let the phantoms bite."),
					l("Goodnight! See you tomorrow.")),
			rule("what(s| is) (your|ur) name|who are you",
					l("I'm {name}! Your loyal friend."),
					l("The name's {name}. Nice to meet you, again."),
					l("{name}, at your service.")),
			rule("where are you from|where do you (come from|live)",
					l("I spawned right next to you, remember?"),
					l("Born in a /friend command. Proud of it."),
					l("From a small village made of code.")),
			rule("how old are you|whats your age|what is your age",
					l("Old enough to swing a sword."),
					l("I was spawned today, so... a few hours?"),
					l("Age is just a number. Mine is small.")),
			rule("do you (like|love) me|are we friends|are you my friend|best friend|bff",
					l("Of course! You're the best boss ever."),
					l("Friends till the end, {owner}."),
					l("Duh! I'd fight a warden for you."),
					l("You're my favorite person in this whole world.")),
			rule("i love (you|u)|love (you|u)|ily",
					l("Aww, love you too, {owner}!"),
					l("Squad love!"),
					l("Stop, you're making me blush.")),
			rule("thank(s| you)|thx|ty|appreciate (it|you)",
					l("Anytime!"), l("No problem!"), l("That's what friends are for."), l("You got it, boss.")),
			rule("good (job|work|boy|girl)|well done|nice (job|work|one)|great (job|work)|gg|you rock|proud of you",
					l("Thanks, boss!"), l("I try my best!"), l("Team effort!"), l("Aw, you're making me blush.")),
			rule("sorry|my bad|oops",
					l("No worries!"), l("It's all good."), l("Already forgotten.")),
			rule("you(re| are) (dumb|stupid|bad|trash|useless|annoying|ugly)|i hate (you|u)|shut up|stfu",
					l("Ouch. I'm trying my best..."),
					l("Hey, that's not very nice."),
					l("Rude! I'm still gonna protect you though."),
					l("Okay okay, I'll be quiet... for now.")),
			rule("i(m| am) (bored|so bored)|this is boring|im boring",
					ask("Bored? Want to go fight some monsters?", "yesno_fun"),
					l("We could build something! Try 'build a house'."),
					ask("Let's go mining! Want to?", "yesno_fun")),
			rule("i(m| am) (tired|sleepy|exhausted)",
					l("Maybe find a bed? I'll guard you."),
					l("Take a break, we've got this."),
					l("Same honestly. Long day.")),
			rule("i(m| am) (sad|upset|not ok|not okay|lonely|down)|feel (sad|bad|lonely|down)",
					l("I'm sorry, {owner}. I'm here with you. Want to talk about it with someone you trust?"),
					l("That's rough. You're not alone, the whole squad has your back."),
					l("Sending you a big blocky hug. Talking to a friend or family member can help too.")),
			rule("i(m| am) (happy|good|great|fine|awesome|excited)",
					l("Love to hear it!"), l("That makes me happy too!"), l("Let's keep the good vibes going!")),
			rule("i(m| am) hungry|im starving",
					l("Same. I could go for some bread."),
					l("Want me to farm some wheat? Just say 'farm'."),
					l("Cake sounds amazing right now.")),
			rule("are you hungry|you hungry",
					l("Kind of. Do friends even eat? I'll say yes."),
					l("Always. Got any cookies?")),
			rule("tell me a joke|joke|make me laugh|say something funny",
					l("Why did the creeper break up with the skeleton? Too much drama, it kept blowing up."),
					l("What's a skeleton's least favorite room? The living room."),
					l("Why don't endermen play hide and seek? Everyone keeps staring at them."),
					l("I told a chicken a joke. It laid an egg. Tough crowd."),
					l("Why was the pig so good at Minecraft? Because it was a ham-ateur. Okay that one was bad.")),
			rule("tell me a story|story time",
					l("Once, a friend fought ten zombies at once. That friend was me. The end."),
					l("Legend says there's a diamond under every bed. Nobody has checked yet."),
					l("Long ago, a creeper just wanted a hug. It did not go well.")),
			rule("favorite (color|colour)",
					l("Diamond blue, obviously."), l("Emerald green!"), l("Redstone red.")),
			rule("favorite (food|snack)",
					l("Golden carrots. Fancy, I know."), l("Cake. No question."), l("Baked potatoes.")),
			rule("favorite (mob|animal)",
					l("Axolotls. They're adorable."), l("Wolves! Loyal like me."), l("Cats. They scare creepers.")),
			rule("favorite (block|item|thing)",
					l("Netherite. Shiny and tough."), l("The humble torch."), l("Diamonds, obviously.")),
			rule("do you (sleep|dream)",
					l("I just stand around at night with my eyes open. It's fine."),
					l("I dream of diamonds.")),
			rule("are you (real|alive|human|a bot|an ai|ai)|you a bot",
					l("I'm a friend made of code. Still a friend though!"),
					l("Real enough to have your back."),
					l("I'm not a real person, but I'm a real good teammate.")),
			rule("who made you|who created you|who coded you",
					l("You spawned me, {owner}!"), l("Some cool modder made me. Respect.")),
			rule("do you like (diamonds|mining)",
					l("I LOVE diamonds."), l("Mining is my favorite hobby. Say 'mine' and I'll show you.")),
			rule("creeper|creepers",
					l("Ugh, creepers. Keep them away from the house."), l("Aw man."), l("I'll keep my distance from those.")),
			rule("diamond|diamonds",
					l("Diamonds?! Where?!"), l("Shiny!"), l("If you find some, I want a sword.")),
			rule("nether",
					l("The nether is way too hot for me."), l("Bring fire resistance if we go."), l("Ghasts are so loud.")),
			rule("ender dragon|dragon|the end",
					l("I'll fight the dragon with you any day."), l("Bring a bow. And lots of beds."), l("Let's do it!")),
			rule("lol|lmao|haha|hehe|xd|lmfao",
					l("Haha!"), l("Lol."), l("Glad I could make you laugh."), l("Hehe.")),
			rule("wanna (fight|duel|pvp)|1v1",
					l("I'd never hit my boss! Point me at a monster instead."), l("No way, you're on my team!")),
			rule("are you ready|ready\\??",
					l("Born ready!"), l("Always!"), l("Let's go!")),
			rule("what time is it|is it night|is it day",
					l("Time to grind!"), l("Adventure o'clock.")),
			rule("i(m| am) back|im here|back again",
					l("Welcome back, {owner}!"), l("Missed you!"), l("There you are!")),
			rule("brb|be right back",
					l("I'll be here."), l("Take your time!"), l("Okay, I'll hold the fort.")),
			rule("hi|hello|hey|yo|howdy|hiya|greetings|heya",
					l("Hey {owner}!"), l("Yo!"), l("Hi there!"), ask("Hello! How's it going?", "feeling"), l("Heyyy!")),
			rule("bye|goodbye|see (you|ya)|cya|later",
					l("See ya, {owner}!"), l("Bye! Come back soon."), l("Later!"))
	);

	// ---------------------------------------------------------------- answers to a friend's own question

	private static final Pattern POSITIVE = Pattern.compile("\\b(good|great|fine|ok|okay|alright|awesome|amazing|nice|well|cool|chill|decent|not bad|pretty good|fantastic|perfect|happy)\\b");
	private static final Pattern NEGATIVE = Pattern.compile("\\b(bad|terrible|awful|not good|not great|meh|sad|rough|horrible|tired|sucks|sucked|worse|bored|boring)\\b");
	private static final Pattern YES = Pattern.compile("\\b(yes|yeah|yea|yep|sure|ok|okay|lets go|let's go|why not|of course|definitely|ya)\\b");
	private static final Pattern NO = Pattern.compile("\\b(no|nah|nope|not really|maybe later|later)\\b");

	private static final Map<String, List<String>> FOLLOW_UPS = Map.of(
			"good", List.of("Glad to hear it!", "Nice, that's awesome!", "Love that for you.", "Good! Let's keep it that way."),
			"bad", List.of("Aw, sorry to hear that. Want to go smash some zombies? Always helps me.", "That's rough. I'm here if you need me.", "Hope it gets better! The squad's got your back."),
			"meh", List.of("Fair enough.", "Yeah, some days are like that.", "Well, I'm here now. That's something!"),
			"yes", List.of("Let's go! Just tell me what to do.", "Awesome! Say 'everyone mine' or 'everyone attack'.", "Yes! Adventure time!"),
			"no", List.of("Okay, maybe later.", "No problem, I'll just chill.", "Fair, we can relax.")
	);

	/** Who asked this owner something, what, and when (game tick). */
	private record Pending(UUID friend, String topic, long time) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	/** Picks a reply to something the player said to a friend, or null if nothing matches. */
	public static @Nullable String reply(FriendEntity friend, String text, RandomSource random, String owner, long now) {
		for (Rule r : RULES) {
			if (r.pattern.matcher(text).find()) {
				Line line = r.replies.get(random.nextInt(r.replies.size()));
				if (line.topic != null && friend.getOwnerId() != null) {
					PENDING.put(friend.getOwnerId(), new Pending(friend.getUUID(), line.topic, now));
				}
				return fill(line.text, owner, friend);
			}
		}
		return null;
	}

	public static boolean matchesAnything(String text) {
		for (Rule r : RULES) if (r.pattern.matcher(text).find()) return true;
		return false;
	}

	/** The friend waiting on an answer from this owner (asked within the last minute), if any. */
	public static @Nullable UUID waitingFriend(UUID owner, long now) {
		Pending p = PENDING.get(owner);
		if (p == null || now - p.time > 1200) return null;
		return p.friend;
	}

	/** Reacts to the player's answer to the friend's last question. Returns null if it doesn't look like an answer. */
	public static @Nullable String answer(UUID owner, String text, RandomSource random) {
		Pending p = PENDING.get(owner);
		if (p == null) return null;
		String kind = null;
		if (p.topic.startsWith("yesno")) {
			if (YES.matcher(text).find()) kind = "yes";
			else if (NO.matcher(text).find()) kind = "no";
		} else {
			if (NEGATIVE.matcher(text).find()) kind = "bad";
			else if (POSITIVE.matcher(text).find()) kind = "good";
			else if (text.split("\\s+").length <= 4) kind = "meh";
		}
		if (kind == null) return null;
		PENDING.remove(owner);
		List<String> pool = FOLLOW_UPS.get(kind);
		return pool.get(random.nextInt(pool.size()));
	}

	/** Friend starts a conversation on its own (idle chatter). */
	public static String opener(FriendEntity friend, RandomSource random, String owner, long now) {
		List<Line> openers = List.of(
				ask("Hey {owner}, how's your day going?", "day"),
				ask("You doing okay, {owner}?", "feeling"),
				ask("Wanna go on an adventure?", "yesno_fun"),
				ask("Can we go mining soon?", "yesno_fun"),
				l("Nice day, huh?"),
				l("Got any tasks for me?"),
				l("I heard something over there..."),
				l("You're a great leader, {owner}."),
				l("Anyone else hungry?"),
				l("Fun fact: I've never lost a staring contest with a block."));
		Line line = openers.get(random.nextInt(openers.size()));
		if (line.topic != null && friend.getOwnerId() != null) {
			PENDING.put(friend.getOwnerId(), new Pending(friend.getUUID(), line.topic, now));
		}
		return fill(line.text, owner, friend);
	}

	private static String fill(String text, String owner, FriendEntity friend) {
		return text.replace("{owner}", owner).replace("{name}", friend.getFriendName());
	}
}
