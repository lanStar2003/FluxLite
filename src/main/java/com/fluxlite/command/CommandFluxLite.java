package com.fluxlite.command;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.DimensionManager;

import com.fluxlite.backend.GTWirelessBackend;
import com.fluxlite.chunk.ChunkLoadManager;
import com.fluxlite.core.Settlement;
import com.fluxlite.core.registry.Registry;
import com.fluxlite.util.Fmt;

import gregtech.common.misc.spaceprojects.SpaceProjectManager;

/**
 * /fluxlite balance [player] | add &lt;eu&gt; [player] | team [player] | status | settle
 * <p>
 * "balance" and "add" are the M0 check: read and change a team's GT wireless balance through the same backend the
 * connectors use.
 */
public class CommandFluxLite extends CommandBase {

    @Override
    public String getCommandName() {
        return "fluxlite";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/fluxlite <balance|add|team|status|settle>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    private static boolean isOp(ICommandSender sender) {
        return sender.canCommandSenderUseCommand(2, "");
    }

    private static UUID target(ICommandSender sender, String[] args, int idx) {
        if (args.length > idx) {
            if (!isOp(sender)) throw new WrongUsageException("fluxlite.cmd.op_only");
            EntityPlayerMP p = getPlayer(sender, args[idx]);
            return p.getUniqueID();
        }
        if (sender instanceof EntityPlayerMP p) return p.getUniqueID();
        throw new WrongUsageException("fluxlite.cmd.need_player");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        GTWirelessBackend b = GTWirelessBackend.INSTANCE;
        switch (args[0]) {
            case "balance" -> {
                UUID u = target(sender, args, 1);
                BigInteger bal = b.getBalance(u);
                sender
                    .addChatMessage(new ChatComponentTranslation("fluxlite.cmd.balance", bal.toString(), Fmt.eu(bal)));
            }
            case "add" -> {
                if (!isOp(sender)) throw new WrongUsageException("fluxlite.cmd.op_only");
                if (args.length < 2) throw new WrongUsageException("/fluxlite add <eu> [player]");
                BigInteger amount;
                try {
                    amount = new BigInteger(args[1]);
                } catch (NumberFormatException e) {
                    throw new WrongUsageException("/fluxlite add <eu> [player]");
                }
                UUID u = target(sender, args, 2);
                b.ensureUser(u);
                boolean ok = b.add(u, amount);
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        ok ? "fluxlite.cmd.add_ok" : "fluxlite.cmd.add_fail",
                        amount.toString(),
                        b.getBalance(u)
                            .toString()));
            }
            case "team" -> {
                UUID u = target(sender, args, 1);
                UUID leader = b.resolveTeam(u);
                String name = SpaceProjectManager.getPlayerNameFromUUID(leader);
                sender.addChatMessage(new ChatComponentTranslation("fluxlite.cmd.team", name, leader.toString()));
            }
            case "status" -> {
                Registry reg = Registry.get();
                int records = reg == null ? 0
                    : reg.all()
                        .size();
                sender.addChatMessage(
                    new ChatComponentTranslation(
                        "fluxlite.cmd.status",
                        Settlement.live()
                            .size(),
                        records,
                        b.isAvailable() ? "OK" : b.lastError()));
                for (Integer dim : DimensionManager.getIDs()) {
                    int chunks = ChunkLoadManager.INSTANCE.forcedChunks(dim);
                    if (chunks > 0) sender.addChatMessage(
                        new ChatComponentText(
                            "  dim " + dim
                                + ": "
                                + chunks
                                + " chunks / "
                                + ChunkLoadManager.INSTANCE.tickets(dim)
                                + " tickets"));
                }
            }
            case "settle" -> {
                if (!isOp(sender)) throw new WrongUsageException("fluxlite.cmd.op_only");
                Settlement.settle();
                sender.addChatMessage(new ChatComponentText("FluxLite: settled"));
            }
            default -> throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1)
            return getListOfStringsMatchingLastWord(args, "balance", "add", "team", "status", "settle");
        if (args.length == 2 && !args[0].equals("add") || args.length == 3 && args[0].equals("add"))
            return getListOfStringsMatchingLastWord(
                args,
                MinecraftServer.getServer()
                    .getAllUsernames());
        return new ArrayList<>();
    }
}
