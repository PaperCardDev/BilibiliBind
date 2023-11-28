package cn.paper_card.bilibili_bind;

import cn.paper_card.bilibili_bind.api.*;
import cn.paper_card.bilibili_bind.api.exception.AlreadyBoundException;
import cn.paper_card.bilibili_bind.api.exception.UidHasBeenBoundException;
import cn.paper_card.database.api.DatabaseApi;
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
import java.util.List;
import java.util.UUID;

class BilibiliBindApiImpl implements BilibiliBindApi {

    private final @NotNull BilibiliUtil bilibiliUtil;

    private final @NotNull BindServiceImpl bindService;
    private final @NotNull BindCodeServiceImpl bindCodeService;
    private final @NotNull ConfirmCodeService confirmCodeService;

    private final @NotNull Logger logger;

    private final @NotNull ConfigManager configManager;

    private BilibiliUtil.VideoInfo videoInfo = null;

    BilibiliBindApiImpl(@NotNull DatabaseApi.MySqlConnection important,
                        @NotNull DatabaseApi.MySqlConnection unimportant,
                        @NotNull Logger logger,
                        @NotNull ConfigManager configManager
    ) {
        this.logger = logger;
        this.configManager = configManager;

        this.bilibiliUtil = new BilibiliUtil();

        this.bindService = new BindServiceImpl(important);
        this.bindCodeService = new BindCodeServiceImpl(unimportant);
        this.confirmCodeService = new ConfirmCodeService(unimportant);
    }

    @NotNull BilibiliUtil.VideoInfo getVideoInfo() throws Exception {
        if (this.videoInfo == null) {
            final String bvid = this.configManager.getBvid();
            if (!bvid.isEmpty())
                this.videoInfo = this.bilibiliUtil.requestVideoByBvid(bvid);
            else
                throw new Exception("服主没有配置B站视频链接");
        }
        return this.videoInfo;
    }

    void handleException(@NotNull String msg, @NotNull Throwable e) {
        this.logger.error(msg, e);
    }

    private @NotNull Logger getLogger() {
        return this.logger;
    }

    void setVideoInfo() {
        this.videoInfo = null;
    }

    void testBilibili() {

        final BilibiliUtil.VideoInfo videoInfo;
        try {
            videoInfo = this.getVideoInfo();
        } catch (Exception e) {
            this.handleException("getVideoInfo", e);
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

    @NotNull ConfigManager getConfigManager() {
        return configManager;
    }

    @NotNull ConfirmCodeService getConfirmCodeService() {
        return this.confirmCodeService;
    }

    @NotNull BilibiliUtil getBilibiliUtil() {
        return this.bilibiliUtil;
    }

    void close() {
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


    private @NotNull PreLoginResponse kickBindCode(int code, @NotNull BilibiliUtil.VideoInfo videoInfo) {

        final TextComponent.Builder text = Component.text();
        text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("绑定验证码：").color(NamedTextColor.YELLOW));
        text.append(Component.text(code).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.UNDERLINED));


        text.appendNewline();
        text.append(Component.text("# 自助绑定方法 #").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("请给最新宣传视频三连支持（长按点赞）并在评论区发送评论：").color(NamedTextColor.RED));


        String reply = this.configManager.getReplyFormat();
        reply = reply.replace("%code%", "%d".formatted(code));

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

        text.appendNewline();
        text.append(Component.text("视频标题：").color(NamedTextColor.GREEN));
        text.append(Component.text(videoInfo.title()).color(NamedTextColor.GOLD));

        text.appendNewline();
        text.append(Component.text("发布者：%s (%d)".formatted(videoInfo.ownerName(), videoInfo.ownerId())).color(NamedTextColor.GREEN));

        text.appendNewline();
        text.append(Component.text("提示：可以通过B站的关键字搜索快速找到该视频").color(NamedTextColor.GREEN));

        text.appendNewline();
        text.append(Component.text("成功三连并发布评论之后再次连接服务器即可~").color(NamedTextColor.GREEN));

        return new PreLoginResponse(false, text.build());
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

    private @NotNull PreLoginResponse kickUidHasBeenBind(@NotNull BindInfo info, @NotNull String biliName) {
        // 已经被绑定

        final TextComponent.Builder text = Component.text();
        text.append(Component.text("[ Bilibili绑定 ]").color(NamedTextColor.AQUA));

        text.appendNewline();
        text.append(Component.text("该B站账号 %s (%d) 已经被绑定！".formatted(biliName, info.uid())).color(NamedTextColor.RED));

        text.appendNewline();
        text.append(Component.text("他的游戏角色：%s (%s)".formatted(info.name(), info.uuid().toString())).color(NamedTextColor.GRAY));

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

        // 查询绑定
        final BindInfo bindInfo;

        try {
            bindInfo = this.bindService.queryByUuid(uuid);
        } catch (SQLException e) {
            return this.kickWhenException(e);
        }

        // 已经绑定
        if (bindInfo != null) {
            return new PreLoginResponse(true, null);
        }

        // 没有绑定B站账号

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
            videoInfo = this.getVideoInfo();
        } catch (Exception e) {
            return this.kickWhenException(e);
        }


        if (bindCodeInfo == null) {
            // 没有生成绑定验证码，生成一个

            final int code;

            try {
                code = this.bindCodeService.createCode(uuid, name);
            } catch (SQLException e) {
                return this.kickWhenException(e);
            }

            return this.kickBindCode(code, videoInfo);
        }


        // todo: 节流

        // 已经生成绑定验证码了，检查绑定

        final List<BilibiliUtil.Reply> replies;

        try {
            replies = this.bilibiliUtil.requestLatestReplies(videoInfo.aid());
        } catch (Exception e) {
            return this.kickWhenException(e);
        }

        final BilibiliUtil.Reply matchReply = this.findMatchReply(replies, bindCodeInfo.code());


        if (matchReply == null) return this.kickReplyNotFound(bindCodeInfo.code());

        // 检查账号等级和大会员
        if (matchReply.level() < 4 && !matchReply.isVip()) {

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
            return this.kickUidHasBeenBind(e.getBindInfo(), matchReply.name());
        } catch (SQLException e) {
            return this.kickWhenException(e);
        }

        return this.kickBindOk(name, uuid.toString(), matchReply.name(), "%d".formatted(matchReply.uid()));
    }
}