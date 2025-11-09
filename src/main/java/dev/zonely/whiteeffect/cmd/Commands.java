package dev.zonely.whiteeffect.cmd;

import dev.zonely.whiteeffect.Core;
import dev.zonely.whiteeffect.cash.CashManager;
import dev.zonely.whiteeffect.player.fake.FakeManager;
import dev.zonely.whiteeffect.utils.PunishmentManager;

import java.util.Arrays;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;

public abstract class Commands extends Command {
   public Commands(String name, String... aliases) {
      super(name);
      this.setAliases(Arrays.asList(aliases));

      try {
         SimpleCommandMap simpleCommandMap = (SimpleCommandMap) Bukkit.getServer().getClass()
               .getDeclaredMethod("getCommandMap").invoke(Bukkit.getServer());
         simpleCommandMap.register(this.getName(), "zcore", this);
      } catch (ReflectiveOperationException var4) {
         Core.getInstance().getLogger().log(Level.SEVERE, "Unable to register command: ", var4);
      }

   }

   public static void setupCommands() {
      new CoreCommand();
      new ProfileCommand();
      new MessageCommand();
      new PunishCommand(new PunishmentManager());
         new CashCommand();
      if (!FakeManager.isBungeeSide()) {
         new FakeCommand();
      }

   }

   public abstract void perform(CommandSender var1, String var2, String[] var3);

   public boolean execute(CommandSender sender, String commandLabel, String[] args) {
      this.perform(sender, commandLabel, args);
      return true;
   }
}

