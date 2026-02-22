package com.example.commandsorcery.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.UUID;

@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {
    private static final HashMap<UUID, String> lastCommand = new HashMap<>();

    private int getLevenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) return 100;
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else {
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + (s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1));
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

    // CHANGED: Use CallbackInfo instead of CallbackInfoReturnable
    // CHANGED: Removed the 'Integer' return type check in the parameters
    @Inject(method = "execute", at = @At("HEAD"))
    private void onCommandExecute(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfo ci) {
        ServerCommandSource source = parseResults.getContext().getSource();
        
        // Safety check to ensure the command is actually coming from a player
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            String fullCommand = command.trim();
            UUID uuid = player.getUuid();

            // Ignore simple commands that are likely for testing/utility (optional)
            if (fullCommand.length() < 3) return;

            // Prevention: Don't reward the exact same string twice in a row
            if (fullCommand.equals(lastCommand.get(uuid))) {
                return; 
            }

            String prev = lastCommand.get(uuid);
            int distance = getLevenshteinDistance(fullCommand, prev);

            // If the player only changed 3 characters in a long command, penalize them
            if (distance < 4) {
                player.sendMessage(Text.literal("§c⚠️ Stale Syntax: Try something new!"), true);
                return;
            }

            int reward = calculateXp(fullCommand);
            player.addExperience(reward);
            
            // Sending as overlay text (true)
            player.sendMessage(Text.literal("§b✨ Syntax Power: +" + reward + " XP"), true);
            
            lastCommand.put(uuid, fullCommand);
        }
    }

    private int calculateXp(String cmd) {
        double score = 5.0; // Base XP

        // 1. Reward Length (diminishing returns to prevent 'long gibberish' farming)
        score += Math.sqrt(cmd.length()) * 2;

        // 2. Heavy reward for NBT and JSON structures
        int brackets = (int) cmd.chars().filter(ch -> ch == '{' || ch == '[').count();
        score += (brackets * 25);

        // 3. Reward for complex selectors (@e, @a, @s with arguments)
        if (cmd.contains("@") && cmd.contains("[")) score += 40;

        // 4. "The Logic Bonus" - using execute subcommands
        if (cmd.contains("run ") || cmd.contains("as ") || cmd.contains("at ")) {
            score *= 1.5; // 50% multiplier for scripted commands
        }

        return (int) score;
    }
}