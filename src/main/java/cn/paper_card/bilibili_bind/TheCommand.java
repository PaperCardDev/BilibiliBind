package cn.paper_card.bilibili_bind;

import cn.paper_card.mc_command.TheMcCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

class TheCommand extends TheMcCommand.HasSub {

    private final @NotNull Permission permission;

    private final @NotNull BilibiliBind plugin;

    TheCommand(@NotNull BilibiliBind plugin) {
        super("bili-bind");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission(this.getLabel() + "." + "command"));

        final PluginCommand command = plugin.getCommand(this.getLabel());
        assert command != null;
        command.setExecutor(this);
        command.setTabCompleter(this);

        this.addSubCommand(new Set());
        this.addSubCommand(new PlayerX());
        this.addSubCommand(new Uid());
        this.addSubCommand(new Code());
        this.addSubCommand(new Check());
        this.addSubCommand(new Reload());
        this.addSubCommand(new BindCode());
        this.addSubCommand(new Confirm());
    }

    @Override
    protected boolean canNotExecute(@NotNull CommandSender commandSender) {
        return !commandSender.hasPermission(this.permission);
    }

    private class Set extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Set() {
            super("set");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            // <玩家名或UUID> <B站UID>

            final String argPlayer = strings.length > 0 ? strings[0] : null;
            final String argUid = strings.length > 1 ? strings[1] : null;

            if (argPlayer == null) {
                plugin.sendError(commandSender, "你必须提供参数：玩家名或UUID");
                return true;
            }

            if (argUid == null) {
                plugin.sendError(commandSender, "你必须提供参数：Bilibili的UID");
                return true;
            }

            final long uid;

            try {
                uid = Long.parseLong(argUid);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的Bilibili的UID".formatted(argUid));
                return true;
            }


            plugin.getTaskScheduler().runTaskAsynchronously(() -> {

                final UUID uuid = plugin.parseArgPlayer(argPlayer);

                if (uuid == null) {
                    plugin.sendError(commandSender, "找不到该玩家: %s".formatted(argPlayer));
                    return;
                }


                String name;

                final OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
                name = offlinePlayer.getName();
                if (name == null) {
                    try {
                        name = plugin.getMojangProfileApi().requestByUuid(uuid).name();
                    } catch (Exception e) {
                        e.printStackTrace();
                        plugin.sendError(commandSender, e.toString());
                        return;
                    }
                }

                if (uid == 0) { // 删除绑定
                    final boolean deleted;

                    try {
                        deleted = plugin.deleteBindByUuid(uuid);
                    } catch (Exception e) {
                        e.printStackTrace();
                        plugin.sendError(commandSender, e.toString());
                        return;
                    }

                    if (!deleted) {
                        plugin.sendWarning(commandSender, "没有删除任何数据，可能玩家 %s 没有绑定B站账号".formatted(name));
                        return;
                    }

                    plugin.sendInfo(commandSender, "成功删除了玩家 %s 的B站账号绑定".formatted(name));
                    return;
                }

                final UUID uuid1;
                try {
                    uuid1 = plugin.queryUuid(uid);
                } catch (SQLException e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (uuid1 != null) {
                    plugin.sendWarning(commandSender, "B站UID[%d]已经被玩家%s绑定！".formatted(uid, plugin.getPlayerName(uuid1)));
                    return;
                }

                final boolean added;


                try {
                    added = plugin.addOrUpdateByUuid(uuid, name, uid, "set指令设置，%s执行".formatted(commandSender.getName()));
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                plugin.sendInfo(commandSender, "%s成功，将玩家[%s]的B站UID设置为: %d".formatted(added ? "添加" : "更新", name, uid));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argPlayer = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (argPlayer.isEmpty()) {
                    list.add("<玩家名或UUID>");
                } else {
                    for (OfflinePlayer offlinePlayer : plugin.getServer().getOfflinePlayers()) {
                        final String name = offlinePlayer.getName();
                        if (name == null) continue;
                        if (name.startsWith(argPlayer)) list.add(name);
                    }
                }
                return list;
            }

            if (strings.length == 2) {
                final String argUid = strings[1];
                if (argUid.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<B站UID>");
                    return list;
                }
            }
            return null;
        }
    }

    private class PlayerX extends TheMcCommand {

        private final @NotNull Permission permission;

        protected PlayerX() {
            super("player");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            final String argPlayer = strings.length > 0 ? strings[0] : null;

            final UUID uuid;
            if (argPlayer == null) {
                if (commandSender instanceof Player p) {
                    uuid = p.getUniqueId();
                } else {
                    plugin.sendError(commandSender, "当不提供[玩家名或UUID]参数时，该命令应该由玩家来执行");
                    return true;
                }
            } else {
                uuid = plugin.parseArgPlayer(argPlayer);

                if (uuid == null) {
                    plugin.sendError(commandSender, "找不到该玩家: %s".formatted(argPlayer));
                    return true;
                }
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final Long uid;
                try {
                    uid = plugin.queryBilibiliUid(uuid);
                } catch (SQLException e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                final String name = plugin.getPlayerName(uuid);

                if (uid == null) {
                    plugin.sendWarning(commandSender, "该玩家[%s]没有绑定B站UID".formatted(name));
                    return;
                }

                plugin.sendBindInfo(commandSender, uuid.toString(), name, "%d".formatted(uid));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argPlayer = strings[0];
                final LinkedList<String> list = new LinkedList<>();
                if (argPlayer.isEmpty()) {
                    list.add("[玩家名或UUID]");
                } else {
                    for (OfflinePlayer offlinePlayer : plugin.getServer().getOfflinePlayers()) {
                        final String name = offlinePlayer.getName();
                        if (name == null) continue;
                        if (name.startsWith(argPlayer)) list.add(name);
                    }
                }
                return list;
            }

            return null;
        }
    }


    private class Uid extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Uid() {
            super("uid");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argUid = strings.length > 0 ? strings[0] : null;

            if (argUid == null) {
                plugin.sendError(commandSender, "你必须提供参数：B站UID");
                return true;
            }

            final long uid;

            try {
                uid = Long.parseLong(argUid);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的B站UID".formatted(argUid));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final UUID uuid;
                try {
                    uuid = plugin.queryUuid(uid);
                } catch (SQLException e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (uuid == null) {
                    plugin.sendWarning(commandSender, "该B站UID[%d]没有被任何玩家绑定".formatted(uid));
                    return;
                }

                final String name = plugin.getPlayerName(uuid);

                plugin.sendBindInfo(commandSender, uuid.toString(), name, argUid);
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String string = strings[0];
                if (string.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<B站UID>");
                    return list;
                }
            }
            return null;
        }
    }

    private class BindCode extends TheMcCommand {

        private final @NotNull Permission permission;

        protected BindCode() {
            super("bind-code");
            this.permission = new Permission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argCode = strings.length > 0 ? strings[0] : null;
            final String argUid = strings.length > 1 ? strings[1] : null;

            if (argCode == null) {
                plugin.sendError(commandSender, "必须提供参数：绑定验证码");
                return true;
            }

            if (argUid == null) {
                plugin.sendError(commandSender, "必须提供参数：B站UID");
                return true;
            }

            final int code;

            try {
                code = Integer.parseInt(argCode);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的验证码".formatted(argCode));
                return true;
            }

            final long uid;

            try {
                uid = Long.parseLong(argUid);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的B站UID".formatted(argUid));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {

                final BilibiliBindApi.BindCodeInfo bindCodeInfo;

                try {
                    bindCodeInfo = plugin.getBindCodeApi().takeByCode(code);
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (bindCodeInfo == null) {
                    plugin.sendWarning(commandSender, "验证码 %d 不存在或已经过期失效".formatted(code));
                    return;
                }

                final boolean added;

                try {
                    added = plugin.addOrUpdateByUuid(bindCodeInfo.uuid(), bindCodeInfo.name(), uid, "bind-code指令，%s执行".formatted(commandSender.getName()));
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                plugin.sendInfo(commandSender, "%s绑定成功，游戏名：%s".formatted(
                        added ? "添加" : "更新", bindCodeInfo.name()
                ));

                plugin.sendBindInfo(commandSender, bindCodeInfo.uuid().toString(), bindCodeInfo.name(), "%d".formatted(uid));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argCode = strings[0];
                if (argCode.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<验证码>");
                    return list;
                }
                return null;
            }

            if (strings.length == 2) {
                final String argUid = strings[1];
                if (argUid.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<B站UID>");
                    return list;
                }
            }

            return null;
        }
    }

    private class Code extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Code() {
            super("code");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            if (!(commandSender instanceof final Player player)) {
                plugin.sendError(commandSender, "该命令只能由玩家来执行！");
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final Long uid;

                try {
                    uid = plugin.queryBilibiliUid(player.getUniqueId());
                } catch (SQLException e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (uid != null) {
                    plugin.sendWarning(commandSender, "你已经绑定了一个B站账号，无需生成绑定验证码，B站UID: %d".formatted(uid));
                    return;
                }


                final int code;

                try {
                    code = plugin.getBindCodeApi().createCode(player.getUniqueId(), player.getName());
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (code <= 0) {
                    plugin.sendWarning(commandSender, "生成绑定验证码失败，请重试");
                    return;
                }


                final String str = "白名单验证码%d".formatted(code);

                final TextComponent.Builder text = Component.text();
                text.append(Component.text(str)
                        .color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(str))
                        .hoverEvent(HoverEvent.showText(Component.text("点击复制")))
                );

                final String bvid = plugin.getBvid();
                if (!bvid.isEmpty()) {
                    final String link = "https://www.bilibili.com/video/" + bvid;

                    text.appendNewline();
                    text.append(Component.text("请在B站最新宣传视频评论区发送以上内容").color(NamedTextColor.GREEN));

                    text.appendNewline();
                    text.append(Component.text("视频链接：").color(NamedTextColor.GREEN));
                    text.append(Component.text(link)
                            .color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(link))
                            .hoverEvent(HoverEvent.showText(Component.text("点击打开")))
                    );

                    final String cmd = "/bili-bind check";
                    text.appendNewline();
                    text.append(Component.text("发布该评论后，请执行指令或直接点击：").color(NamedTextColor.GREEN));
                    text.append(Component.text(cmd).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand(cmd))
                            .hoverEvent(HoverEvent.showText(Component.text("点击执行"))));
                }

                plugin.sendInfo(commandSender, text.build());
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

    private class Check extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Check() {
            super("check");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (!(commandSender instanceof final Player player)) {
                plugin.sendError(commandSender, "该命令只能由玩家来执行！");
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final BilibiliBindApi.BindCodeInfo bindCodeInfo;

                try {
                    bindCodeInfo = plugin.getBindCodeApi().takeByUuid(player.getUniqueId());
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (bindCodeInfo == null) {
                    plugin.sendWarning(commandSender, "你尚未生成验证码或验证码已经过期失效，请尝试重新生成绑定验证码");
                    return;
                }

                // 扫描评论区
                final String bvid = plugin.getBvid();
                if (bvid.isEmpty()) {
                    plugin.sendError(commandSender, "服主还没有配置B站视频链接");
                    return;
                }

                final long aid;

                try {
                    aid = plugin.getVideoInfo().aid();
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                final List<BilibiliUtil.Reply> replies;

                try {
                    replies = plugin.getBilibiliUtil().requestLatestReplies(aid);
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                // 按时间排序
                replies.sort((o1, o2) -> {
                    final long time = o1.time();
                    final long time1 = o2.time();
                    return Long.compare(time, time1);
                });

                // 遍历评论
                final long cur = System.currentTimeMillis();
                final String str = "白名单验证码%d".formatted(bindCodeInfo.code());

                BilibiliUtil.Reply match = null;

                for (BilibiliUtil.Reply reply : replies) {
                    final long time = reply.time();
                    if (cur > time + 5 * 60 * 1000L) continue;

                    final String message = reply.message();

                    if (str.equals(message)) {
                        match = reply;
                        break;
                    }
                }

                if (match == null) {
                    plugin.sendWarning(commandSender, "在视频的评论区未找到指定评论");
                    return;
                }

                if (match.level() < 4 && !match.isVip()) {
                    plugin.sendWarning(commandSender, "无法绑定，你的B站账号（%s）等级过低（%d级），" +
                            "等级达到4级或者大会员用户才可自助绑定，" +
                            "你应该提供账号资料截图和账号安全中心截图给管理员让他手动绑定");
                    return;
                }

                final long uid = match.uid();

                final boolean added;

                try {
                    added = plugin.addOrUpdateByUuid(player.getUniqueId(), player.getName(), uid, "check命令，%s执行".formatted(commandSender.getName()));
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                plugin.sendInfo(commandSender, "%s绑定成功，您的B站用户名：%s，如果错误，请联系管理员".formatted(
                        added ? "添加" : "更新",
                        match.name()));

                plugin.sendBindInfo(commandSender, player.getUniqueId().toString(), player.getName(), "%d".formatted(uid));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

    private class Reload extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Reload() {
            super("reload");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            plugin.reloadConfig();
            plugin.setVideoInfo(); // 清除缓存
            plugin.sendInfo(commandSender, "已重载配置");
            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            return null;
        }
    }

    private class Confirm extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Confirm() {
            super("confirm");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + "." + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            final String argCode = strings.length > 0 ? strings[0] : null;

            if (argCode == null) {
                plugin.sendError(commandSender, "没有提供参数：确认验证码");
                return true;
            }

            final int code;

            try {
                code = Integer.parseInt(argCode);
            } catch (NumberFormatException e) {
                plugin.sendError(commandSender, "%s 不是正确的验证码".formatted(argCode));
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final ConfirmCodeApi.Info info;

                try {
                    info = plugin.getConfirmCodeApi().takeCode(code);
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                if (info == null) {
                    plugin.sendWarning(commandSender, "不存在的确认验证码：%d".formatted(code));
                    return;
                }

                // 检查是否已经被绑定
                {
                    final UUID uuid;

                    try {
                        uuid = plugin.queryUuid(info.uid());
                    } catch (SQLException e) {
                        e.printStackTrace();
                        plugin.sendError(commandSender, e.toString());
                        return;
                    }

                    if (uuid != null) {
                        final OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
                        plugin.sendWarning(commandSender, "该B站UID[%d]已经被 %s 绑定".formatted(
                                info.uid(), offlinePlayer.getName()
                        ));
                        return;
                    }
                }

                final boolean added;

                try {
                    added = plugin.addOrUpdateByUuid(info.uuid(), info.name(), info.uid(), "B站昵称：%s，confirm指令，%s执行"
                            .formatted(info.biliName(), commandSender.getName()));
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                plugin.sendInfo(commandSender, "%s成功，已经玩家 %s 的B站账号设置为：%s (%d)".formatted(
                        added ? "添加" : "更新", info.name(), info.biliName(), info.uid()
                ));
                plugin.sendBindInfo(commandSender, info.uuid().toString(), info.name(), "%d".formatted(info.uid()));
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String code = strings[0];
                if (code.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<确认验证码>");
                    return list;
                }
            }
            return null;
        }
    }
}