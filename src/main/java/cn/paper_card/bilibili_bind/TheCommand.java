package cn.paper_card.bilibili_bind;

import cn.paper_card.MojangProfileApi;
import cn.paper_card.bilibili_bind.api.BindCodeInfo;
import cn.paper_card.bilibili_bind.api.BindInfo;
import cn.paper_card.bilibili_bind.api.BindService;
import cn.paper_card.bilibili_bind.api.exception.AlreadyBoundException;
import cn.paper_card.bilibili_bind.api.exception.UidHasBeenBoundException;
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

class TheCommand extends TheMcCommand.HasSub {

    private final @NotNull Permission permission;

    private final @NotNull ThePlugin plugin;

    TheCommand(@NotNull ThePlugin plugin) {
        super("bili-bind");
        this.plugin = plugin;
        this.permission = Objects.requireNonNull(plugin.getServer().getPluginManager().getPermission(this.getLabel() + "." + "command"));

        final PluginCommand command = plugin.getCommand(this.getLabel());
        assert command != null;
        command.setExecutor(this);
        command.setTabCompleter(this);

        this.addSubCommand(new Add());
        this.addSubCommand(new Remove());
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

    private @NotNull List<String> tabCompletePlayerName(@NotNull String arg, @NotNull String tip) {
        final LinkedList<String> list = new LinkedList<>();
        if (arg.isEmpty()) {
            list.add(tip);
        } else {
            for (OfflinePlayer offlinePlayer : plugin.getServer().getOfflinePlayers()) {
                final String name = offlinePlayer.getName();
                if (name == null) continue;
                if (name.startsWith(arg)) list.add(name);
            }
        }
        return list;
    }

    private class Add extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Add() {
            super("add");
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


                final MojangProfileApi.Profile profile = plugin.parseArgPlayer(argPlayer);

                if (profile == null) {
                    plugin.sendError(commandSender, "找不到该玩家: %s".formatted(argPlayer));
                    return;
                }


                String name = profile.name();
                if (name == null) {
                    try {
                        name = plugin.getMojangProfileApi().requestByUuid(profile.uuid()).name();
                    } catch (Exception e) {
                        plugin.getBilibiliBindApi().handleException("mojang profile api request by uuid", e);
                        plugin.sendError(commandSender, e.toString());
                        return;
                    }
                }

                final BindInfo info = new BindInfo(
                        profile.uuid(), name, uid,
                        "add指令，%s执行".formatted(commandSender.getName()),
                        System.currentTimeMillis()
                );

                try {
                    plugin.getBilibiliBindApi().getBindService().addBind(info);
                } catch (AlreadyBoundException | UidHasBeenBoundException e) {
                    plugin.sendWarning(commandSender, e.getMessage());
                    return;
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("bind service add bind", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                plugin.sendInfo(commandSender, "添加绑定成功");
                plugin.sendBindInfo(commandSender, info);
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argPlayer = strings[0];
                return tabCompletePlayerName(argPlayer, "<玩家名或UUID>");
            }

            if (strings.length == 2) {
                final String argUid = strings[1];
                if (argUid.isEmpty()) {
                    final LinkedList<String> list = new LinkedList<>();
                    list.add("<B站UID>");
                    return list;
                }
                return null;
            }
            return null;
        }
    }

    private class Remove extends TheMcCommand {

        private final @NotNull Permission permission;

        protected Remove() {
            super("remove");
            this.permission = plugin.addPermission(TheCommand.this.permission.getName() + '.' + this.getLabel());
        }

        @Override
        protected boolean canNotExecute(@NotNull CommandSender commandSender) {
            return !commandSender.hasPermission(this.permission);
        }

        @Override
        public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {

            // <玩家名或UUID>

            final String argPlayer = strings.length > 0 ? strings[0] : null;

            if (argPlayer == null) {
                plugin.sendError(commandSender, "你必须提供参数：玩家名或UUID");
                return true;
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {


                final MojangProfileApi.Profile profile = plugin.parseArgPlayer(argPlayer);

                if (profile == null) {
                    plugin.sendError(commandSender, "找不到该玩家: %s".formatted(argPlayer));
                    return;
                }


                final BindService service = plugin.getBilibiliBindApi().getBindService();

                final BindInfo bindInfo;

                // 查询
                try {
                    bindInfo = service.queryByUuid(profile.uuid());
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("bind service query by uuid", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (bindInfo == null) {
                    plugin.sendWarning(commandSender, "该玩家没有绑定B站UID");
                    return;
                }

                // 删除
                try {
                    service.removeBind(bindInfo.uuid(), bindInfo.uid());
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("bind service remove binding", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                plugin.sendInfo(commandSender, "删除绑定成功");
                plugin.sendBindInfo(commandSender, bindInfo);
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                return tabCompletePlayerName(strings[0], "<玩家名或UUID>");
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

            final MojangProfileApi.Profile profile;
            if (argPlayer == null) {
                if (commandSender instanceof Player p) {
                    profile = new MojangProfileApi.Profile(p.getName(), p.getUniqueId());
                } else {
                    plugin.sendError(commandSender, "当不提供[玩家名或UUID]参数时，该命令应该由玩家来执行");
                    return true;
                }
            } else {
                profile = plugin.parseArgPlayer(argPlayer);

                if (profile == null) {
                    plugin.sendError(commandSender, "找不到该玩家: %s".formatted(argPlayer));
                    return true;
                }
            }

            plugin.getTaskScheduler().runTaskAsynchronously(() -> {

                final BindInfo bindInfo;

                try {
                    bindInfo = plugin.getBilibiliBindApi().getBindService().queryByUuid(profile.uuid());
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("player command -> bind service -> query by uuid", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (bindInfo == null) {
                    plugin.sendWarning(commandSender, "该玩家没有绑定B站UID");
                    return;
                }

                plugin.sendBindInfo(commandSender, bindInfo);
            });

            return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
            if (strings.length == 1) {
                final String argPlayer = strings[0];
                return tabCompletePlayerName(argPlayer, "[玩家名或UUID]");
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
                final BindInfo bindInfo;

                try {
                    bindInfo = plugin.getBilibiliBindApi().getBindService().queryByUid(uid);
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("uid command -> bind service -> query by uid", e);
                    plugin.sendException(commandSender, e);
                    return;
                }


                if (bindInfo == null) {
                    plugin.sendWarning(commandSender, "该B站UID[%d]没有被任何玩家绑定".formatted(uid));
                    return;
                }

                plugin.sendBindInfo(commandSender, bindInfo);
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

                final BindCodeInfo bindCodeInfo;

                try {
                    bindCodeInfo = plugin.getBilibiliBindApi().getBindCodeService().takeByCode(code);
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("bind-code command -> bind code service -> take by code", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (bindCodeInfo == null) {
                    plugin.sendWarning(commandSender, "验证码 %d 不存在或已过期失效".formatted(code));
                    return;
                }


                final BindInfo bindInfo = new BindInfo(
                        bindCodeInfo.uuid(), bindCodeInfo.name(),
                        uid, "bind-code指令，%s执行".formatted(commandSender.getName()), System.currentTimeMillis()
                );

                try {
                    plugin.getBilibiliBindApi().getBindService().addBind(bindInfo);
                } catch (AlreadyBoundException | UidHasBeenBoundException e) {
                    plugin.sendWarning(commandSender, e.getMessage());
                    return;
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("bind-code command -> bind service -> add bind", e);
                    plugin.sendException(commandSender, e);
                    return;
                }


                plugin.sendInfo(commandSender, "通过验证码添加绑定成功");
                plugin.sendBindInfo(commandSender, bindInfo);
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

                final BilibiliBindApiImpl api = plugin.getBilibiliBindApi();

                final List<String> list = api.getConfigManager().getBvid();
                if (list.isEmpty()) {
                    plugin.sendError(commandSender, "没有配置用于验证的B站视频");
                    return;
                }

                final BindInfo bindInfo;

                try {
                    bindInfo = api.getBindService().queryByUuid(player.getUniqueId());
                } catch (Exception e) {
                    api.handleException("code command -> bind service -> query by uuid", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (bindInfo != null) {
                    plugin.sendWarning(commandSender, "你已经绑定了一个B站账号，无需生成绑定验证码");
                    plugin.sendBindInfo(commandSender, bindInfo);
                    return;
                }

                final int code;

                try {
                    code = api.getBindCodeService().createCode(player.getUniqueId(), player.getName());
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("code command -> bind code service -> create code", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                final String str = api.getConfigManager().getReplay(code);

                final TextComponent.Builder text = Component.text();
                text.append(Component.text(str)
                        .color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.copyToClipboard(str))
                        .hoverEvent(HoverEvent.showText(Component.text("点击复制")))
                );

                final String bvid = list.get(0);

                if (!bvid.isEmpty()) {
                    final String link = BilibiliUtil.getVideoLink(bvid);

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
                final BindCodeInfo bindCodeInfo;

                final BilibiliBindApiImpl api = plugin.getBilibiliBindApi();

                try {
                    bindCodeInfo = api.getBindCodeService().takeByUuid(player.getUniqueId());
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("check command -> bind code service -> take by uuid", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (bindCodeInfo == null) {
                    plugin.sendWarning(commandSender, "你尚未生成验证码或验证码已过期失效，请尝试重新生成绑定验证码");
                    return;
                }

                final BilibiliUtil.Reply matchReply;

                // 扫描评论区
                try {
                    matchReply = api.findReply(bindCodeInfo.code());
                } catch (Exception e) {
                    api.handleException("check command -> getVideoInfo", e);
                    plugin.sendException(commandSender, e);
                    return;
                }


                if (matchReply == null) {
                    plugin.sendWarning(commandSender, "在视频的评论区未找到指定评论");
                    return;
                }


                final BindInfo info = new BindInfo(player.getUniqueId(), player.getName(), matchReply.uid(),
                        "check指令，%s执行，B站昵称：%s，等级：%d，大会员：%s".formatted(
                                commandSender.getName(), matchReply.name(), matchReply.level(), matchReply.isVip()),
                        System.currentTimeMillis());

                try {
                    api.getBindService().addBind(info);
                } catch (AlreadyBoundException | UidHasBeenBoundException e) {
                    plugin.sendWarning(commandSender, e.getMessage());
                    return;
                } catch (Exception e) {
                    api.handleException("check command -> bind service -> add bind", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                plugin.sendInfo(commandSender, "添加绑定成功");
                plugin.sendBindInfo(commandSender, info);
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
            plugin.getTaskScheduler().runTaskAsynchronously(() -> {
                final BilibiliBindApiImpl api = plugin.getBilibiliBindApi();
                api.getBindService().clearCache(); // 清除缓存
                api.getConfigManager().reload();
                api.updateAllVideoInfo();
                plugin.sendInfo(commandSender, "已重载配置、清除缓存");
            });
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


                final ConfirmCodeService.Info info;

                try {
                    info = plugin.getBilibiliBindApi().getConfirmCodeService().takeCode(code);
                } catch (SQLException e) {
                    plugin.getBilibiliBindApi().handleException("confirm command -> confirm code service -> take code", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                if (info == null) {
                    plugin.sendWarning(commandSender, "不存在的确认验证码：%d".formatted(code));
                    return;
                }

                final BindInfo bindInfo = new BindInfo(
                        info.uuid(), info.name(), info.uid(),
                        "B站昵称：%s，confirm指令，%s执行".formatted(info.biliName(), commandSender.getName()),
                        System.currentTimeMillis()
                );

                try {
                    plugin.getBilibiliBindApi().getBindService().addBind(bindInfo);
                } catch (AlreadyBoundException | UidHasBeenBoundException e) {
                    plugin.sendWarning(commandSender, e.getMessage());
                    return;
                } catch (Exception e) {
                    plugin.getBilibiliBindApi().handleException("confirm command -> bind service -> add bind", e);
                    plugin.sendException(commandSender, e);
                    return;
                }

                plugin.sendInfo(commandSender, "添加绑定成功");
                plugin.sendBindInfo(commandSender, bindInfo);
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