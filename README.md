# ZonelyCore

<p align="center">
  <a href="https://builtbybit.com/resources/zonely-website-for-game-servers-4-etc.79906/">
    <strong>This plugin was made for the Zonely “All Built Into One” Minecraft edition.</strong>
  </a>
</p>

---

## Why Zonely?

Zonely is a powerful software platform that offers innovative solutions in e-commerce, whose development was started by our company on January 2, 2025. This software brings many digital domains together under one roof. Such as in-game marketing, digital marketing, promotional marketing, distributed server marketing, and domain management. Thanks to its advanced integrations and user-friendly interface, it enables users to set up their websites easily and establish a strong presence in the digital world. With its high-performance, ultra-secure infrastructure, Zonely strengthens its partners’ digital assets and aims to elevate them to the top of their industries.

## ZonelyCore Plugin Introduction

Our plugin is designed to make your game server compatible with your website. You can sync the categories and products you’ve added to your website into the game. Players can purchase in-game using their site credits and share their purchases. With SecureSocket support, you can send commands to your game server from your website. There’s even more in the plugin. We recommend taking a look.

## Features

- **Webstore |** Players can view and purchase the products on your website via an in-game GUI. If you want to change the contents of products and product categories, log in to your admin panel.
- **Auctions |** Players can list their items in the Auctions section on your website and in-game without any fee by setting a credit amount. Players with sufficient credits can claim/purchase the auctioned item.
- **Punishments |** Your staff can penalize a player—mute, ban, warn, and view their record history. You can manage penalized users from your website, and players can view them publicly.
- **Last Credits |** The latest credit top-ups are shown in the GUI, filtered by category.
- **Leaderboards |** Recent credit top-ups can be displayed as a hologram. The hologram is clickable and can be viewed by category.
- **Credits |** You can manage your players’ credits.
- **Vouchers |** This feature lets your players convert their site credits into a paper voucher item. They can share credits with each other using this paper. When right-clicked, the credits are added to the player’s account.
- **Reports |** Players can report users they believe are violating rules to the staff. Staff can view the reported player via a GUI and take action. Integrated with the Punishments system. Thanks to the Replay system, staff can watch the player’s actions live/in real time.
- **WebSockets |** Applies to product sales on the site. Uses encrypted data to securely execute website commands in-game. Ensures that only site-verified commands are executed by the console.
- **NPCs |** You can create NPCs that open 6 different menus. Supports Hologram and PlaceholderAPI.
- **Deliveries |** Players can claim time-based rewards via a GUI.
- **Web Profiles |** A player’s website profile can be viewed in-game via a GUI.
- **Leagues |** Leagues can be advanced based on achievements earned on the server. Viewable via GUI.
- **Groups |** By assigning permissions through another plugin, you can grant users additional privileges. Usable in the profile menu.
- **Boosters |** A GUI to accelerate league achievements. With boosters obtained from the Rewards section, players can rank up faster.
- **Titles |** You can assign titles to a user. When selected via the GUI, the placeholder renders wherever you specify. Example: **Title Player:** message
- **Chat Colors |** Users with permission can change their chat message colors via the GUI; messages appear in a different color.
- **Languages |** Players can choose their display language from the menu.
- **Socials |** Players can configure the social media accounts that will appear on the site, directly from in-game.
- **Disguises |** Opens a book GUI that lets the player appear under a different identity in-game.
- **Chat Filters |** Block player-submitted ads and profane messages, and automatically issue penalties.
- **Replays |** Watch reported players as if you were watching a video, and issue penalties.
- **PlaceholderAPI & MySQL Support |** The plugin can connect to the database, and you can view the placeholders below.
- **Login & Register |** Players log in using the same account they create on the web panel.
- **Auto Login |** After the first login or registration, trusted IPs are remembered and players are sent straight into the server without being asked to log in again.
- **Bossbar Prompts |** Custom bossbars guide players through login, registration, and email verification steps in-game.
- **Fallback Server |** After a successful login or registration, players are automatically transferred to a predefined BungeeCord server.
- **E-mail Verification |** When enabled, players are asked to set an email; a verification code is sent to their inbox and, once entered in-game, they can continue playing.
- **2FA (TOTP) |** TOTP-based two-factor authentication lets players confirm their login with a one-time code before entering the game.
- **Email Change |** To change their email, players receive a one-time code in their inbox; entering this code in-game securely updates their email address.
- **Password Reset |** Players request a token with the `/passwordreset` command in-game and safely set a new password via the website, under time and security constraints.
- **ChatBridge |** Syncs website chat with the game server; players can pick a server on the site, send commands or messages, and see them live alongside the list of online players.
- **Inventory Snapshots |** Whenever players join or leave, their inventory is captured and shown as a GUI on their web profile, optionally visible to other players.
- **Launcher Auth |** Your server’s custom ZonelyLauncher is required for access; players joining without it are kicked with a download link and can only play through the launcher.
- **Support Tickets |** Website support tickets can be managed live from in-game; staff can reply, close, or ban, and players can open new tickets from the server by choosing a support category.

## Commands

- `/zc`
- `/webprofile`
- `/store`
- `/credit`
- `/support`
- `/2fa`
- `/passwordreset`
- `/email set`
- `/email resend`
- `/email status`
- `/login`
- `/register`
- `/lastcredits`
- `/auctions`
- `/reports`
- `/disguise`
- `/credit [player]`
- `/credit set [player] [amount]`
- `/credit add [player] [amount]`
- `/credit remove [player] [amount]`
- `/creditvoucher [player] [amount]`
- `/report [player]`
- `/auction [amount]`
- `/punish`
- `/punish ban/ipban/mute/warning [player] [duration] [reason]`
- `/punish unban/unmute/unwarning [player]`
- `/zc npc spawn/remove web-profile/auctions/last-credits/reports/deliveries/web-store/supports [id]`
- `/zc hologram lastcredits [id]`

## Permissions

- `zonely.auction`
- `zonely.auction.manage`
- `zonely.reports.view`
- `zonely.punish`
- `zonely.punish.ban`
- `zonely.punish.mute`
- `zonely.punish.warning`
- `zonely.punish.history`
- `zonely.punish.notify`
- `zonely.punish.ipban`
- `zonely.support.staff`
- `zonely.support.ban`

## Placeholders

- `%ZonelyCore_cash%`
- `%ZonelyCore_perm%`
- `%ZonelyCore_playername%`
- `%ZonelyCore_status_delivery%`
- `%ZonelyCore_lastcredit_<number>_display%`
- `%ZonelyCore_lastcredit_<number>%`
- `%ZonelyCore_lastcredit_<number>_name%`
- `%ZonelyCore_lastcredit_<number>_amount%`
- `%ZonelyCore_lastcredit_<number>_date%`
- `%ZonelyCore_lastcredit_<number>_time%`

## Screenshots & Stats

![ZonelyCore Showcase](https://i.hizliresim.com/2qvzq52.png)

![bStats: ZonelyCores](https://bstats.org/signatures/bukkit/ZonelyCores.svg)

## ZonelyCore API Quick Reference

All examples assume ZonelyCore is enabled and your plugin depends on it.

<details>
<summary><strong>ZonelyCore API</strong></summary>

### Accessing the API

```java
import dev.zonely.whiteeffect.api.ZonelyCoreAPI;

public final class MyAddon {

    private ZonelyCoreAPI api;

    public void onEnable() {
        api = ZonelyCoreAPI.get(); // safe once ZonelyCore is enabled
    }
}
```

### Checking Module Toggles

```java
if (!api.areVouchersEnabled()) {
    getLogger().warning("Vouchers module disabled, skipping custom voucher logic.");
}

if (api.areLastCreditsEnabled()) {
    getLogger().info("Last Credits module active — holograms and GUI calls are safe.");
}
```

### Cash API (credits)

`getBalance(name|player)` returns the current credit total (offline-safe).  
`addBalance`, `withdraw`, `setBalance` return the updated balance and throw `CreditOperationException` on failure (insufficient funds, DB issues, validation errors).  
`isCached` / `invalidateCache` let you manage the in-memory cache after manual DB edits.  
Async helpers (`addBalanceAsync`, `withdrawAsync`, `setBalanceAsync`) complete on a `CompletableFuture<Long>` so you can stay off the main thread.

```java
import dev.zonely.whiteeffect.api.cash.CashAPI;
import dev.zonely.whiteeffect.api.cash.CreditOperationException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
// 'plugin' below should be your JavaPlugin instance

CashAPI cash = api.cash();

try {
    long updated = cash.addBalance("PlayerOne", 500);
    long remaining = cash.withdraw("PlayerTwo", 250);
    cash.setBalance("PlayerVIP", 10_000);
    getLogger().info("Balances updated: " + updated + " / " + remaining);
} catch (CreditOperationException ex) {
    getLogger().warning("Credit operation failed: " + ex.getMessage());
}

cash.addBalanceAsync("AsyncGuy", 1000).thenAccept(newBalance ->
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayerExact("AsyncGuy");
            if (player != null) {
                player.sendMessage("Your new balance: " + newBalance);
            }
        })
);
```

### Voucher API

`issueVoucher(issuer, target, amount)` deducts credits and returns a `VoucherIssueResult` (item + metadata).  
`parseVoucher(item)` inspects items safely, `redeemVoucher(details|item)` credits the target.  
Throws `CreditOperationException` for validation problems (e.g., insufficient funds).

```java
import dev.zonely.whiteeffect.api.voucher.VoucherAPI;
import dev.zonely.whiteeffect.api.voucher.VoucherIssueResult;
import dev.zonely.whiteeffect.api.cash.CreditOperationException;
// assumes 'player' is available

VoucherAPI vouchers = api.vouchers();

try {
    VoucherIssueResult voucher = vouchers.issueVoucher("Staff", "PlayerTwo", 250);
    player.getInventory().addItem(voucher.getVoucherItem());
    vouchers.redeemVoucher(voucher.getDetails());
} catch (CreditOperationException ex) {
    getLogger().warning("Voucher error: " + ex.getMessage());
}
```

### Deliveries API

`listDeliveries()` exposes configured reward entries.  
`getStatus(player, delivery)` describes permission/cooldown; `claim(player, delivery)` performs the reward (throws `CreditOperationException` on profile load failures).

```java
import dev.zonely.whiteeffect.api.cash.CreditOperationException;
// assumes 'player' is available

api.deliveries().listDeliveries().forEach(delivery -> {
    api.deliveries().getStatus(player, delivery).ifPresent(status -> {
        if (status.canClaimNow()) {
            try {
                api.deliveries().claim(player, delivery);
            } catch (CreditOperationException ex) {
                player.sendMessage("Delivery failed: " + ex.getMessage());
            }
        }
    });
});
```

### Last Credits API (leaderboard & menu)

`getLeaderboard(category)` returns cached entries (RECENT, ALL_TIME, YEARLY, MONTHLY, DAILY).  
`getLastLeaderboardRefresh(category)` exposes snapshot freshness.  
`getRecentTopups()` mirrors the PlaceholderAPI cache.  
`getViewerCategory(player)` / `setViewerCategory(player, category)` keep holograms & GUI aligned per player.  
`openMenu(player, category)` honors the viewer’s active category (pass `getViewerCategory` to stay in sync).

```java
import dev.zonely.whiteeffect.menu.lastcredits.LastCreditsMenuManager;
// assumes 'player' is available

var recent = api.lastCredits().getLeaderboard(LastCreditsMenuManager.Category.RECENT);
recent.forEach(entry ->
        Bukkit.getLogger().info(entry.getPosition() + ". " + entry.getUsername() + " -> " + entry.getFormattedAmount())
);

LastCreditsMenuManager.Category active = api.lastCredits().getViewerCategory(player);
api.lastCredits().openMenu(player, active);
```

### Secure Socket API

Controlled via `config.yml` (`web-token`, `socket-port`); module toggle must be enabled.  
`start()`, `stop()`, `restart()` manage the listener; `isActive()` and `getPort()` expose runtime state.

```java
if (api.isSocketEnabled()) {
    if (!api.socket().isActive()) {
        if (!api.socket().start()) {
            getLogger().warning("Secure socket could not start. Check config.yml.");
        }
    } else {
        api.socket().restart();
    }
}
```

### Auction API

`openMainMenu`, `openSoldMenu`, `openPurchasedMenu`, `openPurchaseMenu` are safe from async threads (manager switches back to Bukkit).  
`getMenuManager()` exposes the GUI controller; `getDao()` grants direct DB access for custom workflows.  
Check `api.areAuctionsEnabled()` before exposing custom commands.

```java
import dev.zonely.whiteeffect.api.auction.AuctionDao;
// assumes 'player' is available

if (api.areAuctionsEnabled()) {
    api.auctions().openMainMenu(player);

    AuctionDao dao = api.auctions().getDao();
    if (dao != null) {
        dao.listByCreator(dao.fetchUserId(player.getName()))
                .forEach(item -> getLogger().info(item.getCustomTitle()));
    }
} else {
    player.sendMessage("Auctions are disabled on this server.");
}
```

</details>

## Contributing

We welcome contributions from the community! If you would like to contribute, please follow these guidelines:

1. Fork the repository and clone it to your local machine.
2. Create a new branch for your feature or bug fix.
3. Make your changes, and ensure that your code is well-tested.
4. Create a pull request with a detailed description of your changes.

By contributing to this project, you agree to abide by the [Code of Conduct](README.md).

## License

This project is licensed under the [MIT License](LICENSE).
