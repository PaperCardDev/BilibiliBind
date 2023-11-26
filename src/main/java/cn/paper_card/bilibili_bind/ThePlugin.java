package cn.paper_card.bilibili_bind;

import cn.paper_card.MojangProfileApi;
import cn.paper_card.database.DatabaseApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.NoSuchElementException;
import java.util.UUID;

public class ThePlugin extends JavaPlugin {

    private final @NotNull TaskScheduler taskScheduler;
    private final @NotNull TextComponent prefix;

    private final @NotNull BilibiliBindApiImpl bilibiliBindApi;

    private final @NotNull MojangProfileApi mojangProfileApi;

    public ThePlugin() {
        final DatabaseApi api = this.getDatabaseApi0();

        @NotNull ConfigManagerImpl configManager = new ConfigManagerImpl(this);

        this.bilibiliBindApi = new BilibiliBindApiImpl(
                api.getRemoteMySqlDb().getConnectionImportant(),
                api.getRemoteMySqlDb().getConnectionUnimportant(),
                this.getLogger(),
                configManager
        );

        this.taskScheduler = UniversalScheduler.getScheduler(this);

        this.prefix = Component.text()
                .append(Component.text("[").color(NamedTextColor.GOLD))
                .append(Component.text("Bilibili绑定").color(NamedTextColor.DARK_AQUA))
                .append(Component.text("]").color(NamedTextColor.GOLD))
                .build();

        this.mojangProfileApi = new MojangProfileApi();


    }

    @Override
    public void onLoad() {
        this.getServer().getServicesManager().register(BilibiliBindApi.class, this.bilibiliBindApi, this, ServicePriority.Highest);

        this.bilibiliBindApi.testBilibili();
    }

    @Override
    public void onEnable() {
        new TheCommand(this);
    }

    @Override
    public void onDisable() {

        this.getServer().getServicesManager().unregisterAll(this);
        this.bilibiliBindApi.close();
    }

    @Nullable MojangProfileApi.Profile parseArgPlayer(@NotNull String argPlayer) {
        try {
            final UUID uuid = UUID.fromString(argPlayer);
            return new MojangProfileApi.Profile(null, uuid);
        } catch (IllegalArgumentException ignored) {
        }

        for (OfflinePlayer offlinePlayer : this.getServer().getOfflinePlayers()) {
            final String name = offlinePlayer.getName();
            if (argPlayer.equals(name)) {
                return new MojangProfileApi.Profile(name, offlinePlayer.getUniqueId());
            }
        }
        return null;
    }

    private @NotNull DatabaseApi getDatabaseApi0() {
        final Plugin plugin = this.getServer().getPluginManager().getPlugin("Database");
        if (plugin instanceof DatabaseApi api) {
            return api;
        } else throw new NoSuchElementException("Database插件未安装");
    }

    @NotNull MojangProfileApi getMojangProfileApi() {
        return this.mojangProfileApi;
    }

    @NotNull BilibiliBindApiImpl getBilibiliBindApi() {
        return this.bilibiliBindApi;
    }

    void sendError(@NotNull CommandSender sender, @NotNull String error) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(error).color(NamedTextColor.RED))
                .build());
    }

    void sendException(@NotNull CommandSender sender, @NotNull Exception e) {
        final TextComponent.Builder text = Component.text();
        text.append(this.prefix);
        text.appendSpace();

        text.append(Component.text("==== 异常信息 ====").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }

        sender.sendMessage(text.build());
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull String info) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(info).color(NamedTextColor.GREEN))
                .build());
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull TextComponent info) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(info)
                .build()
        );
    }

    void sendWarning(@NotNull CommandSender sender, @NotNull String warning) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(warning).color(NamedTextColor.YELLOW))
                .build());
    }

    void sendBindInfo(@NotNull CommandSender sender, @NotNull BilibiliBindApi.BindInfo info) {

        final String uuid = info.uuid().toString();
        final String uid = "%d".formatted(info.uid());
        final String link = BilibiliUtil.getSpaceLink(info.uid());

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日_HH:mm:ss");
        final String datetime = simpleDateFormat.format(info.time());


        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text("==== Bilibili绑定信息 ====").color(NamedTextColor.GREEN))
                .appendNewline()
                .append(Component.text("游戏名: ").color(NamedTextColor.GREEN))
                .append(Component.text(info.name()).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(info.name()))
                        .hoverEvent(HoverEvent.showText(Component.text("点击复制"))))
                .appendNewline()

                .append(Component.text("UUID: ").color(NamedTextColor.GREEN))
                .append(Component.text(uuid).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(uuid))
                        .hoverEvent(HoverEvent.showText(Component.text("点击复制"))))
                .appendNewline()

                .append(Component.text("B站UID: ").color(NamedTextColor.GREEN))
                .append(Component.text(uid).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(uid))
                        .hoverEvent(HoverEvent.showText(Component.text("点击复制"))))


                .appendNewline()
                .append(Component.text("B站个人空间: ").color(NamedTextColor.GREEN))
                .append(Component.text(link).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(link))
                        .hoverEvent(HoverEvent.showText(Component.text("点击打开")))
                )

                .appendNewline()
                .append(Component.text("备注：").color(NamedTextColor.GREEN))
                .append(Component.text(info.remark()).color(NamedTextColor.GREEN))

                .appendNewline()
                .append(Component.text("时间：").color(NamedTextColor.GREEN))
                .append(Component.text(datetime).color(NamedTextColor.GREEN))

                .build()
        );
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }


    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }


}
