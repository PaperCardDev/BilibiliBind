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
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.UUID;

public final class BilibiliBind extends JavaPlugin implements BilibiliBindApi {

    private final DatabaseApi.MySqlConnection mySqlConnection;
    private Table table = null;
    private Connection connection = null;

    private final @NotNull TextComponent prefix;

    private final @NotNull TaskScheduler taskScheduler;

    private final @NotNull MojangProfileApi mojangProfileApi;

    public BilibiliBind() {
        this.mySqlConnection = this.getDatabaseApi0().getRemoteMySqlDb().getConnectionImportant();
        this.prefix = Component.text()
                .append(Component.text("[").color(NamedTextColor.GOLD))
                .append(Component.text("BILI绑定").color(NamedTextColor.DARK_AQUA))
                .append(Component.text("]").color(NamedTextColor.GOLD))
                .build();

        this.taskScheduler = UniversalScheduler.getScheduler(this);
        this.mojangProfileApi = new MojangProfileApi();
    }

    private @NotNull DatabaseApi getDatabaseApi0() {
        final Plugin plugin = this.getServer().getPluginManager().getPlugin("Database");
        if (plugin instanceof DatabaseApi api) {
            return api;
        } else throw new NoSuchElementException("Database插件未安装");
    }

    private @NotNull Table getTable() throws SQLException {
        final Connection newCon = this.mySqlConnection.getRowConnection();
        if (this.connection == null) {
            this.connection = newCon;
            if (this.table != null) this.table.close();
            this.table = new Table(this.connection);
            return this.table;
        } else if (this.connection == newCon) {
            return this.table;
        } else {
            this.connection = newCon;
            if (this.table != null) this.table.close();
            this.table = new Table(this.connection);
            return this.table;
        }
    }


    @Override
    public void onEnable() {
        new TheCommand(this);
    }

    @Override
    public void onDisable() {
        synchronized (this.mySqlConnection) {
            if (this.table != null) {
                try {
                    this.table.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                this.table = null;
            }
        }
    }

    @Override
    public @Nullable Long queryBilibiliUid(@NotNull UUID uuid) throws SQLException {

        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final Long uid = t.queryUid(uuid);
                this.mySqlConnection.setLastUseTime();
                return uid;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }

        }
    }

    @Override
    public @Nullable UUID queryUuid(long bilibiliUid) throws SQLException {
        synchronized (this.mySqlConnection) {
            final Table t;
            try {
                t = this.getTable();
                final UUID uuid = t.queryUuid(bilibiliUid);
                this.mySqlConnection.setLastUseTime();
                return uuid;
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @Override
    public boolean addOrUpdateByUuid(@NotNull UUID uuid, @NotNull String name, long bilibiliUid) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();
                final long time = System.currentTimeMillis();

                final int updated = t.updateByUuid(uuid, name, bilibiliUid, time);

                this.mySqlConnection.setLastUseTime();

                if (updated == 1) return false;

                if (updated == 0) {
                    final int inserted = t.insert(uuid, name, bilibiliUid, time);
                    this.mySqlConnection.setLastUseTime();

                    if (inserted != 1) throw new Exception("插入了%d条数据！".formatted(inserted));
                    return true;
                }

                throw new Exception("更新了%d条数据！".formatted(updated));
            } catch (SQLException e) {
                try {
                    this.mySqlConnection.checkClosedException(e);
                } catch (SQLException ignored) {
                }
                throw e;
            }
        }
    }

    @NotNull Permission addPermission(@NotNull String name) {
        final Permission permission = new Permission(name);
        this.getServer().getPluginManager().addPermission(permission);
        return permission;
    }

    void sendError(@NotNull CommandSender sender, @NotNull String error) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(error).color(NamedTextColor.RED))
                .build());
    }

    void sendInfo(@NotNull CommandSender sender, @NotNull String info) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(info).color(NamedTextColor.GREEN))
                .build());
    }

    void sendWarning(@NotNull CommandSender sender, @NotNull String warning) {
        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text(warning).color(NamedTextColor.YELLOW))
                .build());
    }

    void sendBindInfo(@NotNull CommandSender sender, @NotNull String uuid, @NotNull String name, @NotNull String uid) {

        // https://space.bilibili.com/391939252
        final String link = "https://space.bilibili.com/" + uid;

        sender.sendMessage(Component.text()
                .append(this.prefix)
                .appendSpace()
                .append(Component.text("==== Bilibili绑定信息 ====").color(NamedTextColor.GREEN))
                .appendNewline()
                .append(Component.text("游戏名: ").color(NamedTextColor.GREEN))
                .append(Component.text(name).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(name))
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

                .build()
        );
    }

    @Nullable UUID parseArgPlayer(@NotNull String argPlayer) {
        try {
            return UUID.fromString(argPlayer);
        } catch (IllegalArgumentException ignored) {
        }

        for (OfflinePlayer offlinePlayer : this.getServer().getOfflinePlayers()) {
            if (argPlayer.equals(offlinePlayer.getName())) {
                return offlinePlayer.getUniqueId();
            }
        }
        return null;
    }

    @NotNull String getPlayerName(@NotNull UUID uuid) {
        final OfflinePlayer offlinePlayer = this.getServer().getOfflinePlayer(uuid);

        final String name = offlinePlayer.getName();
        if (name != null) return name;

        return uuid.toString();
    }

    @NotNull TaskScheduler getTaskScheduler() {
        return this.taskScheduler;
    }

    @NotNull MojangProfileApi getMojangProfileApi() {
        return this.mojangProfileApi;
    }
}
