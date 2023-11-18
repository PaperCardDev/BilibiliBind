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
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

public final class BilibiliBind extends JavaPlugin implements BilibiliBindApi {

    private final DatabaseApi.MySqlConnection mySqlConnection;

    private Table table = null;
    private Connection connection = null;

    private final @NotNull TextComponent prefix;

    private final @NotNull TaskScheduler taskScheduler;

    private final @NotNull MojangProfileApi mojangProfileApi;

    private final @NotNull DatabaseApi databaseApi;

    private final @NotNull BindCodeApiImpl bindCodeApi;

    private final @NotNull BilibiliUtil bilibiliUtil;

    public BilibiliBind() {
        this.databaseApi = this.getDatabaseApi0();
        this.mySqlConnection = this.databaseApi.getRemoteMySqlDb().getConnectionImportant();

        this.prefix = Component.text()
                .append(Component.text("[").color(NamedTextColor.GOLD))
                .append(Component.text("BILI绑定").color(NamedTextColor.DARK_AQUA))
                .append(Component.text("]").color(NamedTextColor.GOLD))
                .build();

        this.taskScheduler = UniversalScheduler.getScheduler(this);
        this.mojangProfileApi = new MojangProfileApi();

        this.bindCodeApi = new BindCodeApiImpl(this);
        this.bilibiliUtil = new BilibiliUtil();
    }

    private @NotNull DatabaseApi getDatabaseApi0() {
        final Plugin plugin = this.getServer().getPluginManager().getPlugin("Database");
        if (plugin instanceof DatabaseApi api) {
            return api;
        } else throw new NoSuchElementException("Database插件未安装");
    }

    @NotNull DatabaseApi getDatabaseApi() {
        return this.databaseApi;
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

    @NotNull String getBvid() {
        return this.getConfig().getString("bvid", "");
    }

    void testBilibili(@NotNull String bvid) {

        final BilibiliUtil.VideoInfo videoInfo;
        try {
            videoInfo = this.bilibiliUtil.requestVideoByBvid(bvid);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        this.getLogger().info("VideoInfo {aid: %d, title: %s, ownerId: %d, ownerName: %s}".formatted(
                videoInfo.aid(), videoInfo.title(), videoInfo.ownerId(), videoInfo.ownerName()
        ));


        // 爬取评论测试
        final List<BilibiliUtil.Reply> replies;

        try {
            replies = this.bilibiliUtil.requestLatestReplies(videoInfo.aid());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
        for (BilibiliUtil.Reply reply : replies) {
            this.getLogger().info("{name: %s, uid: %d, isVip: %s, message: %s, time: %s, level: %d}".formatted(
                    reply.name(),
                    reply.uid(),
                    reply.isVip(),
                    reply.message(),
                    format.format(reply.time()),
                    reply.level()
            ));
        }
    }


    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        new TheCommand(this);

        final String bvid = this.getBvid();
        if (!bvid.isEmpty()) {
            testBilibili(bvid);
        }
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
        this.bindCodeApi.close();
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

    @Override
    public boolean deleteBindByUuid(@NotNull UUID uuid) throws Exception {
        synchronized (this.mySqlConnection) {
            try {
                final Table t = this.getTable();

                final int deleted = t.deleteByUuid(uuid);
                this.mySqlConnection.setLastUseTime();

                if (deleted == 1) return true;
                if (deleted == 0) return false;
                throw new Exception("删除了%d条数据！".formatted(deleted));

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
    public @NotNull BindCodeApi getBindCodeApi() {
        return this.bindCodeApi;
    }

    @Override
    public void onPreLoginCheck(@NotNull AsyncPlayerPreLoginEvent event) {
        final UUID id = event.getUniqueId();

        final Long uid;
        try {
            uid = this.queryBilibiliUid(id);
        } catch (SQLException e) {
            e.printStackTrace();
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(Component.text(e.toString()).color(NamedTextColor.RED));
            return;
        }

        // 已经绑定
        if (uid != null) {
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.ALLOWED);
            this.getLogger().info("玩家%s的B站UID: %d".formatted(event.getName(), uid));
            return;
        }

        // 没有绑定B站账号

        // 检查是否有绑定验证码
        final BindCodeInfo bindCodeInfo;

        try {
            bindCodeInfo = this.getBindCodeApi().takeByUuid(id);
        } catch (Exception e) {
            e.printStackTrace();
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(Component.text(e.toString()).color(NamedTextColor.RED));
            return;
        }


        if (bindCodeInfo == null) {
            // 没有生成绑定验证码，生成一个
            final int code;

            try {
                code = this.getBindCodeApi().createCode(id, event.getName());
            } catch (Exception e) {
                e.printStackTrace();
                event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                event.kickMessage(Component.text()
                        .append(Component.text("生成Bilibili绑定验证码失败，请尝试重新连接").color(NamedTextColor.RED))
                        .appendNewline()
                        .append(Component.text(e.toString()).color(NamedTextColor.RED))
                        .build());
                return;
            }

            final TextComponent.Builder text = Component.text();
            text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));
            text.appendNewline();

            final String str = "白名单验证码%d".formatted(code);

            text.append(Component.text(str)
                    .color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.copyToClipboard(str))
                    .hoverEvent(HoverEvent.showText(Component.text("点击复制")))
            );

            final String bvid = this.getBvid();
            if (!bvid.isEmpty()) {

                final BilibiliUtil.VideoInfo videoInfo;

                try {
                    videoInfo = this.bilibiliUtil.requestVideoByBvid(bvid);
                } catch (Exception e) {
                    e.printStackTrace();
                    event.kickMessage(Component.text(e.toString()).color(NamedTextColor.RED));
                    event.setLoginResult(AsyncPlayerPreLoginEvent.Result.ALLOWED);
                    return;
                }

                text.appendNewline();
                text.append(Component.text("请在最新宣传视频的评论区发送以上信息（不能只发数字）").color(NamedTextColor.RED));

                final String link = "https://www.bilibili.com/video/" + bvid;

                text.appendNewline();
                text.append(Component.text("视频链接：").color(NamedTextColor.GREEN));
                text.append(Component.text(link).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(link))
                        .hoverEvent(HoverEvent.showText(Component.text("点击打开")))
                );

                text.appendNewline();
                text.append(Component.text("视频标题：").color(NamedTextColor.GREEN));
                text.append(Component.text(videoInfo.title()).color(NamedTextColor.GOLD));

                text.appendNewline();
                text.append(Component.text("发布者：%s (%d)".formatted(videoInfo.ownerName(), videoInfo.ownerId())));

                text.appendNewline();
                text.append(Component.text("成功发布评论后再次连接服务器即可~").color(NamedTextColor.GREEN));
            }

            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST);
            event.kickMessage(text.build());
            return;
        }

        // 已经生成绑定验证码了，检查绑定
        final String bvid = this.getBvid();
        if (bvid.isEmpty()) {
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(Component.text("服主没有配置B站视频链接").color(NamedTextColor.RED));
            return;
        }


        final BilibiliUtil.VideoInfo videoInfo;
        try {
            videoInfo = this.bilibiliUtil.requestVideoByBvid(bvid);
        } catch (Exception e) {
            e.printStackTrace();
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(Component.text(e.toString()).color(NamedTextColor.RED));
            return;
        }

        final List<BilibiliUtil.Reply> replies;

        try {
            replies = this.bilibiliUtil.requestLatestReplies(videoInfo.aid());
        } catch (Exception e) {
            e.printStackTrace();
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(Component.text(e.toString()).color(NamedTextColor.RED));
            return;
        }

        // 按时间排序
        replies.sort((o1, o2) -> {
            final long time = o1.time();
            final long time1 = o2.time();
            return Long.compare(time, time1);
        });

        BilibiliUtil.Reply match = null;

        final String str = "白名单验证码%d".formatted(bindCodeInfo.code());

        for (BilibiliUtil.Reply reply : replies) {
            if (str.equals(reply.message())) {
                match = reply;
                break;
            }
        }

        if (match == null) {

            final TextComponent.Builder text = Component.text();

            text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

            text.appendNewline();
            text.append(Component.text("没有在指定视频评论区找到该评论：").color(NamedTextColor.RED));

            text.appendNewline();
            text.append(Component.text(str).color(NamedTextColor.GOLD));

            text.appendNewline();
            text.append(Component.text("你可以再次连接服务器获取新验证码进行重试").color(NamedTextColor.GREEN));

            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(text.build());
            return;
        }

        // 检查账号等级和大会员
        if (match.level() < 4 && !match.isVip()) {
            final TextComponent.Builder text = Component.text();
            text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

            text.appendNewline();
            text.append(Component.text("您的B站账号（%s）等级过低（%d级）".formatted(match.name(), match.level())).color(NamedTextColor.RED));

            text.appendNewline();
            text.append(Component.text("只有等级达到4级或者大会员用户才能自助添加白名单").color(NamedTextColor.RED));

            text.appendNewline();
            text.append(Component.text("可以联系管理员手动添加白名单，管理QQ群[822315449]").color(NamedTextColor.GREEN));

            event.kickMessage(text.build());
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            return;
        }

        // 添加绑定
        final boolean added;

        try {
            added = this.addOrUpdateByUuid(id, event.getName(), match.uid());
        } catch (Exception e) {
            e.printStackTrace();
            event.kickMessage(Component.text(e.toString()).color(NamedTextColor.RED));
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            return;
        }

        this.getLogger().info("%s了玩家 %s B站账号绑定：%d".formatted(
                added ? "添加" : "更新", event.getName(), match.uid()
        ));

        final TextComponent.Builder text = Component.text();
        text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("恭喜！您已成功添加白名单 :D").color(NamedTextColor.GREEN));

        text.appendNewline();
        text.append(Component.text("您的B站账号：%s(%d)".formatted(match.name(), match.uid())).color(NamedTextColor.GREEN));

        text.appendNewline();
        text.append(Component.text("如果绑定错误，请联系管理员").color(NamedTextColor.GREEN));

        text.appendNewline();
        text.append(Component.text("请重新连接服务器").color(NamedTextColor.GOLD));

        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        event.kickMessage(text.build());
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

    @NotNull BilibiliUtil getBilibiliUtil() {
        return this.bilibiliUtil;
    }
}

