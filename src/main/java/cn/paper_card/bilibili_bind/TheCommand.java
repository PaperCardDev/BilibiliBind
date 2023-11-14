package cn.paper_card.bilibili_bind;

import cn.paper_card.mc_command.TheMcCommand;
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

                try {
                    added = plugin.addOrUpdateByUuid(uuid, name, uid);
                } catch (Exception e) {
                    e.printStackTrace();
                    plugin.sendError(commandSender, e.toString());
                    return;
                }

                plugin.sendInfo(commandSender, "%s成功，将玩家[%s]的B站UID设置为: %d".formatted(
                        added ? "添加" : "更新", name, uid
                ));
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
}