package cn.paper_card.bilibili_bind;

import cn.paper_card.bilibili_bind.api.*;
import cn.paper_card.bilibili_bind.api.exception.AlreadyBoundException;
import cn.paper_card.bilibili_bind.api.exception.UidHasBeenBoundException;
import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.qq_bind.api.QqBindApi;
import cn.paper_card.qq_group_access.api.GroupAccess;
import cn.paper_card.qq_group_access.api.QqGroupAccessApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

class BilibiliBindApiImpl implements BilibiliBindApi {

    private final @NotNull BilibiliUtil bilibiliUtil;

    private final @NotNull BindServiceImpl bindService;
    private final @NotNull BindCodeServiceImpl bindCodeService;
    private final @NotNull ConfirmCodeService confirmCodeService;

    private final @NotNull Logger logger;

    private final @NotNull ConfigManagerImpl configManager;

    private final @NotNull Supplier<QqBindApi> qqBindApi;

    private final @NotNull Supplier<QqGroupAccessApi> qqGroupAccessApi;

    private final @NotNull HashMap<String, BilibiliUtil.VideoInfo> videoInfo;

    BilibiliBindApiImpl(@NotNull DatabaseApi.MySqlConnection important,
                        @NotNull DatabaseApi.MySqlConnection unimportant,
                        @NotNull Logger logger,
                        @NotNull ConfigManagerImpl configManager,
                        @NotNull Supplier<QqBindApi> qqBindApi,
                        @NotNull Supplier<QqGroupAccessApi> qqGroupAccessApi) {
        this.logger = logger;
        this.configManager = configManager;
        this.qqBindApi = qqBindApi;
        this.qqGroupAccessApi = qqGroupAccessApi;

        this.bilibiliUtil = new BilibiliUtil();

        this.bindService = new BindServiceImpl(important);
        this.bindCodeService = new BindCodeServiceImpl(unimportant);
        this.confirmCodeService = new ConfirmCodeService(unimportant);
        this.videoInfo = new HashMap<>();
    }


    void handleException(@NotNull String msg, @NotNull Throwable e) {
        this.logger.error(msg, e);
    }

    private @NotNull Logger getLogger() {
        return this.logger;
    }

    void updateAllVideoInfo() {
        final List<String> list = this.configManager.getBvid();

        for (String bvid : list) {
            if (bvid.isEmpty()) continue;
            final BilibiliUtil.VideoInfo info;

            try {
                info = this.bilibiliUtil.requestVideoByBvid(bvid);
            } catch (Exception e) {
                this.logger.error("", e);
                continue;
            }

            synchronized (this.videoInfo) {
                this.videoInfo.put(bvid, info);
            }
        }
    }

    @Nullable BilibiliUtil.Reply findReply(int code) throws Exception {
        final List<BilibiliUtil.VideoInfo> list;
        synchronized (this.videoInfo) {
            list = new LinkedList<>(videoInfo.values());
        }

        for (final BilibiliUtil.VideoInfo info : list) {
            final List<BilibiliUtil.Reply> replies;
            replies = this.bilibiliUtil.requestLatestReplies(info.aid());
            final BilibiliUtil.Reply r = this.findMatchReply(replies, code);
            if (r != null) return r;
        }
        return null;
    }

    @NotNull BilibiliUtil.VideoInfo getFirstVideoInfo() throws Exception {
        final List<String> list = this.configManager.getBvid();
        if (list.isEmpty()) throw new Exception("没有配置用于验证的B站视频！");

        final String id = list.get(0);

        synchronized (this.videoInfo) {
            final BilibiliUtil.VideoInfo info = this.videoInfo.get(id);
            if (info != null) return info;
        }

        final BilibiliUtil.VideoInfo info = this.bilibiliUtil.requestVideoByBvid(id);

        synchronized (this.videoInfo) {
            this.videoInfo.put(id, info);
        }

        return info;
    }

    void testBilibili() {

        this.updateAllVideoInfo();

        BilibiliUtil.VideoInfo first = null;

        synchronized (this.videoInfo) {

            for (BilibiliUtil.VideoInfo videoInfo : this.videoInfo.values()) {

                if (first == null) first = videoInfo;

                this.getLogger().info("VideoInfo {aid: %d, title: %s, ownerId: %d, ownerName: %s}".formatted(
                        videoInfo.aid(), videoInfo.title(), videoInfo.ownerId(), videoInfo.ownerName()
                ));
            }
        }

        if (first == null) return;

        // 爬取评论测试
        final List<BilibiliUtil.Reply> replies;

        try {
            replies = this.bilibiliUtil.requestLatestReplies(first.aid());
        } catch (Exception e) {
            this.handleException("requestLatestReplies", e);
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
    public @NotNull BindServiceImpl getBindService() {
        return this.bindService;
    }

    @Override
    public @NotNull BindCodeService getBindCodeService() {
        return this.bindCodeService;
    }

    @NotNull ConfigManagerImpl getConfigManager() {
        return configManager;
    }

    @NotNull ConfirmCodeService getConfirmCodeService() {
        return this.confirmCodeService;
    }

    void

    init() {
        this.configManager.setDefaults();
        this.configManager.save();

        this.testBilibili();
    }

    void close() {
        this.configManager.save();

        try {
            this.bindService.close();
        } catch (SQLException e) {
            this.handleException("closeBindService", e);
        }

        try {
            final int clean = this.bindCodeService.close();
            this.getLogger().info("清理了%d个过期的验证码".formatted(clean));
        } catch (SQLException e) {
            this.handleException("closeBindCodeService", e);
        }

        try {
            this.confirmCodeService.close();
        } catch (SQLException e) {
            this.handleException("closeConfirmCodeService", e);
        }
    }

    private @NotNull PreLoginResponse kickWhenException(@NotNull Exception e) {

        this.handleException("whenPreLogin", e);

        final TextComponent.Builder text = Component.text();

        text.append(Component.text("[ Bilibili绑定 | 错误 ]").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }

        return new PreLoginResponse(false, text.build());
    }

    private @NotNull PreLoginResponse kickConfirmCode(@NotNull String name, @NotNull UUID uuid, @NotNull String biliName, long uid, @NotNull String level, int code) {

        final TextComponent.Builder text = Component.text();

        text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("你的B站账号等级过低，需要管理员确认B站账号").color(NamedTextColor.RED));

        text.appendNewline();
        text.append(Component.text("只有等级达到4级或者大会员用户才能自助添加绑定").color(NamedTextColor.RED));

        text.appendNewline();
        text.append(Component.text("你的B站账号：%s (%d) 等级：%s".formatted(
                biliName, uid, level
        )).color(NamedTextColor.YELLOW));

        text.appendNewline();
        text.append(Component.text("你的游戏角色：%s (%s)".formatted(
                name, uuid.toString()
        )).color(NamedTextColor.GRAY));

        text.appendNewline();
        text.append(Component.text("确认验证码：").color(NamedTextColor.GOLD));
        text.append(Component.text(code).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.UNDERLINED));
        text.append(Component.text(" （长期有效，提供给管理员使用）").color(NamedTextColor.GOLD));

        text.appendNewline();
        text.append(Component.text("请加入管理QQ群[822315449]提供此页面截图和B站账号的一些截图").color(NamedTextColor.GREEN));

        return new PreLoginResponse(false, text.build());
    }


    private @NotNull PreLoginResponse kickBindCode(int code,
                                                   @NotNull BilibiliUtil.VideoInfo videoInfo,
                                                   @NotNull String name,
                                                   @NotNull UUID uuid,
                                                   @Nullable cn.paper_card.qq_bind.api.BindInfo qqBind,
                                                   @Nullable GroupAccess groupAccess
    ) {


        final TextComponent.Builder text = Component.text();
        text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("绑定验证码：").color(NamedTextColor.YELLOW));
        text.append(Component.text(code).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.UNDERLINED));


        text.appendNewline();
        text.append(Component.text("# 自助绑定方法 #").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("请给最新宣传视频三连支持（长按点赞）并在评论区发送评论：").color(NamedTextColor.RED));

        final String reply = this.configManager.getReplay(code);

        text.appendNewline();
        text.append(Component.text(reply).color(NamedTextColor.GOLD));


        // todo: 不应该写死五分钟
        text.appendNewline();
        text.append(Component.text("验证码有效期：5分钟，重新连接立即失效").color(NamedTextColor.YELLOW));

        final String link = BilibiliUtil.getVideoLink(videoInfo.bvid());

        text.appendNewline();
        text.append(Component.text("视频链接：").color(NamedTextColor.GREEN));
        text.append(Component.text(link).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(Component.text("点击打开")))
        );

        if (qqBind == null && groupAccess != null) {
            text.appendNewline();
            text.append(Component.text("您未绑定QQ，可以直接将数字"));
            text.append(Component.text(code).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            text.append(Component.text("发送到QQ群里进行绑定并获取该视频链接"));

            text.appendNewline();
            text.append(Component.text("QQ群："));
            text.append(Component.text(groupAccess.getId()).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
        }

        text.appendNewline();
        text.append(Component.text("发布者：%s (%d)".formatted(videoInfo.ownerName(), videoInfo.ownerId())).color(NamedTextColor.GREEN));

        text.appendNewline();
        text.append(Component.text("成功三连并发布评论之后再次连接服务器即可~").color(NamedTextColor.GREEN));

        if (qqBind != null) {
            text.appendNewline();
            text.append(Component.text("您的QQ：").color(NamedTextColor.GRAY));
            text.append(Component.text(qqBind.qq()).color(NamedTextColor.GRAY));
        }

        text.appendNewline();
        text.append(Component.text("游戏名：%s (%s)".formatted(
                name, uuid.toString()
        )).color(NamedTextColor.GRAY));

        return new PreLoginResponse(false, text.build().color(NamedTextColor.GREEN));
    }

    private @NotNull PreLoginResponse kickReplyNotFound(int code) {

        String reply = this.configManager.getReplyFormat();
        reply = reply.replace("%code%", "%d".formatted(code));

        final TextComponent.Builder text = Component.text();

        text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("没有在指定的视频评论区找到你的评论：").color(NamedTextColor.RED));

        text.appendNewline();
        text.append(Component.text(reply).color(NamedTextColor.GOLD).decorate(TextDecoration.UNDERLINED));

        text.appendNewline();
        text.append(Component.text("# 可能的原因 #").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("大概率你弄错视频了，或者评论的格式不正确，或者没有三连").color(NamedTextColor.GOLD));

        text.appendNewline();
        text.append(Component.text("你上次的验证码已经失效，可以再次连接服务器获取新验证码进行重试").color(NamedTextColor.GREEN));

        text.appendNewline();
        text.append(Component.text("如需获取帮助，请加入管理QQ群[822315449]").color(NamedTextColor.GRAY));

        return new PreLoginResponse(false, text.build());
    }

    private @NotNull PreLoginResponse kickUidHasBeenBind(@NotNull BindInfo info, @NotNull String biliName, @NotNull String name, @NotNull UUID uuid) {
        // 已经被绑定

        final TextComponent.Builder text = Component.text();
        text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("该B站账号 %s (%d) 已经被绑定！".formatted(biliName, info.uid())).color(NamedTextColor.RED));

        text.appendNewline();
        text.append(Component.text("他的游戏角色：%s (%s)".formatted(info.name(), info.uuid().toString())).color(NamedTextColor.GRAY));

        text.appendNewline();
        text.append(Component.text("你的游戏角色：%s (%s)".formatted(name, uuid.toString())).color(NamedTextColor.GRAY));

        return new PreLoginResponse(false, text.build());
    }

    private @NotNull PreLoginResponse kickBindOk(@NotNull String name, @NotNull String uuid, @NotNull String biliName, @NotNull String uid) {

        final TextComponent.Builder text = Component.text();
        text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("恭喜！您已成功添加白名单 :D").color(NamedTextColor.GREEN));

        text.appendNewline();
        text.append(Component.text("你的B站账号：%s (%s)".formatted(biliName, uid)).color(NamedTextColor.GREEN));

        text.appendNewline();
        text.append(Component.text("你的游戏角色：%s (%s)".formatted(name, uuid)).color(NamedTextColor.GRAY));

        text.appendNewline();
        text.append(Component.text("如果绑定错误，请联系管理员，管理QQ群[822315449]").color(NamedTextColor.YELLOW));

        text.appendNewline();
        text.append(Component.text("请重新连接服务器~").color(NamedTextColor.GOLD));

        return new PreLoginResponse(false, text.build());
    }


    // 匹配的评论
    @Nullable BilibiliUtil.Reply findMatchReply(@NotNull List<BilibiliUtil.Reply> replies, int code) {

        // 按时间排序
        replies.sort((o1, o2) -> {
            final long time = o1.time();
            final long time1 = o2.time();
            return Long.compare(time, time1);
        });

        final long cur = System.currentTimeMillis();

        final String codeStr = "%d".formatted(code);

        for (BilibiliUtil.Reply reply : replies) {
            // 过滤超时的评论
            // todo: 不应该写死五分钟
            if (cur > reply.time() + 5 * 60 * 1000L) continue;

            if (reply.message().contains(codeStr)) {
                return reply;
            }
        }

        return null;
    }

    @Override
    public @NotNull PreLoginResponse handlePreLogin(@NotNull UUID uuid, @NotNull String name) {

        // 查询是否有确认码

        final ConfirmCodeService.Info confrimCodeInfo;

        try {
            confrimCodeInfo = this.confirmCodeService.queryByPlayer(uuid);
        } catch (Exception e) {
            return this.kickWhenException(e);
        }

        // 查询绑定
        final BindInfo bindInfo;

        try {
            bindInfo = this.bindService.queryByUuid(uuid);
        } catch (SQLException e) {
            return this.kickWhenException(e);
        }

        // 已经绑定
        if (bindInfo != null) {

            // 回收确认码
            if (confrimCodeInfo != null) {
                try {
                    final ConfirmCodeService.Info i = this.getConfirmCodeService().takeCode(confrimCodeInfo.code());
                    if (i != null) this.getLogger().info("回收%s的确认验证码：%d".formatted(i.name(), i.code()));
                } catch (SQLException e) {
                    return this.kickWhenException(e);
                }
            }

            return new PreLoginResponse(true, null);
        }

        // 没有绑定B站账号

        if (confrimCodeInfo != null) {
            // todo: 没有存等级？
            return this.kickConfirmCode(name, uuid,
                    confrimCodeInfo.biliName(),
                    confrimCodeInfo.uid(),
                    "TODO: 忘了保存你的等级",
                    confrimCodeInfo.code()
            );
        }

        // 没有确认码

        // 检查是否有绑定验证码
        final BindCodeInfo bindCodeInfo;

        try {
            bindCodeInfo = this.bindCodeService.takeByUuid(uuid);
        } catch (SQLException e) {
            return this.kickWhenException(e);
        }

        // 获取视频信息
        final BilibiliUtil.VideoInfo videoInfo;
        try {
            videoInfo = this.getFirstVideoInfo();
        } catch (Exception e) {
            return this.kickWhenException(e);
        }


        if (bindCodeInfo == null) {
            // 没有生成绑定验证码，生成一个

            // QQ绑定
            final QqBindApi qqBindApi1 = this.qqBindApi.get();

            final cn.paper_card.qq_bind.api.BindInfo qqBindInfo;

            if (qqBindApi1 != null) {
                try {
                    qqBindInfo = qqBindApi1.getBindService().queryByUuid(uuid);
                } catch (Exception e) {
                    return this.kickWhenException(e);
                }
            } else {
                qqBindInfo = null;
            }

            // 绑定验证码
            final int code;
            try {
                code = this.bindCodeService.createCode(uuid, name);
            } catch (SQLException e) {
                return this.kickWhenException(e);
            }


            // QQ群访问
            final QqGroupAccessApi qqGroupAccessApi1 = this.qqGroupAccessApi.get();
            GroupAccess groupAccess = null;
            if (qqGroupAccessApi1 != null) {
                try {
                    groupAccess = qqGroupAccessApi1.createMainGroupAccess();
                } catch (Exception e) {
                    this.logger.warn(e.toString());
                }
            }

            if (groupAccess != null && qqBindInfo != null) {
                try {
                    groupAccess.sendAtMessage(qqBindInfo.qq(), "B站视频：" + BilibiliUtil.getVideoLink(videoInfo.bvid()));
                } catch (Exception e) {
                    this.logger.error("", e);
                }
            }

            return this.kickBindCode(code,
                    videoInfo,
                    name,
                    uuid,
                    qqBindInfo,
                    groupAccess
            );
        }


        // todo: 节流

        // 已经生成绑定验证码了，检查绑定
        final BilibiliUtil.Reply matchReply;

        try {
            matchReply = this.findReply(bindCodeInfo.code());
        } catch (Exception e) {
            return this.kickWhenException(e);
        }

        if (matchReply == null) return this.kickReplyNotFound(bindCodeInfo.code());

        // 检查账号等级和大会员
        final int allowMinLevel = this.configManager.getAllowMinLevel();
        if (matchReply.level() < allowMinLevel && !matchReply.isVip()) {

            final int confirmCode;

            try {
                confirmCode = this.confirmCodeService.getCode(uuid, name, matchReply.uid(), matchReply.name());
            } catch (Exception e) {
                return this.kickWhenException(e);
            }

            return this.kickConfirmCode(name, uuid, matchReply.name(), matchReply.uid(),
                    "%d".formatted(matchReply.level()), confirmCode);
        }

        // 添加绑定
        try {
            this.bindService.addBind(new BindInfo(uuid, name, matchReply.uid(),
                    "验证码自助绑定，昵称：%s，等级：%d，大会员：%s".formatted(matchReply.name(), matchReply.level(), matchReply.isVip()),
                    System.currentTimeMillis()));

        } catch (AlreadyBoundException e) {
            // 不应该出现的
            return this.kickWhenException(e);
        } catch (UidHasBeenBoundException e) {
            return this.kickUidHasBeenBind(e.getBindInfo(), matchReply.name(), name, uuid);
        } catch (SQLException e) {
            return this.kickWhenException(e);
        }

        return this.kickBindOk(name, uuid.toString(), matchReply.name(), "%d".formatted(matchReply.uid()));
    }

    private StringBuilder getMessage(@NotNull String name) {

        BilibiliUtil.VideoInfo info = null;
        try {
            info = this.getFirstVideoInfo();
        } catch (Exception e) {
            this.logger.warn("", e);
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("\n已使玩家 ");
        sb.append(name);
        sb.append(" 的Bilibili绑定验证码失效");
        sb.append("\n应该发在B站指定视频的评论区，而不是QQ群！");
        sb.append("\n请重新连接刷新验证码");

        if (info != null) {
            sb.append("\nB站视频链接：");
            sb.append("\n");
            sb.append(BilibiliUtil.getVideoLink(info.bvid()));
        }

        return sb;
    }

    @Override
    public @Nullable String onMainGroupMessage(@NotNull String message, long senderQq) {

        final int count = this.bindCodeService.getCount();
        if (count <= 0) return null;

        try {
            final int cleaned = this.bindCodeService.cleanOutdated();
            getLogger().info("清理了%d个过期的Bilibili绑定验证码，剩余验证码：%d".formatted(cleaned, this.bindCodeService.getCount()));
        } catch (SQLException e) {
            this.handleException("clean outdated binding code", e);
            return null;
        }

        final int code;
        try {
            code = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            return null;
        }

        final BindCodeInfo bindCodeInfo;

        try {
            bindCodeInfo = this.bindCodeService.takeByCode(code);
        } catch (SQLException e) {
            this.handleException("take by code", e);
            return null;
        }

        if (bindCodeInfo == null) return null;

        // QQ绑定？
        final QqBindApi api = this.qqBindApi.get();
        if (api == null) {
            return this.getMessage(bindCodeInfo.name()).toString();
        }

        final cn.paper_card.qq_bind.api.BindInfo qqBind;

        try {
            qqBind = api.getBindService().queryByQq(senderQq);
        } catch (Exception e) {
            handleException("qq bind -> query by qq", e);
            return null;
        }

        // QQ已经被绑定了
        if (qqBind != null) {
            return this.getMessage(bindCodeInfo.name()).toString();
        }

        // 没有绑定，添加QQ绑定
        final cn.paper_card.qq_bind.api.BindInfo qqBindNew = new cn.paper_card.qq_bind.api.BindInfo(
                bindCodeInfo.uuid(),
                bindCodeInfo.name(),
                senderQq,
                "bilibili绑定验证码",
                System.currentTimeMillis()
        );

        try {
            api.getBindService().addBind(qqBindNew);
        } catch (Exception e) {
            handleException("qq bind -> add bind", e);
            return e.toString();
        }

        final StringBuilder m = this.getMessage(bindCodeInfo.name());
        m.append("\n已添加QQ绑定：");
        m.append("\n游戏名：");
        m.append(qqBindNew.name());
        m.append("\n如果这不是你，请联系管理员");

        return m.toString();
    }
}
