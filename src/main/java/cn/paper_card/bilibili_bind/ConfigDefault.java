package cn.paper_card.bilibili_bind;

import org.jetbrains.annotations.NotNull;

class ConfigDefault implements ConfigManager {
    @Override
    public @NotNull String getBvid() {
        return "";
    }

    @Override
    public @NotNull String getReplyFormat() {
        return "白名单验证码%code%已三连";
    }

    @Override
    public @NotNull String getReplay(int code) {
        String format = this.getReplyFormat();
        format = format.replace("%code%", "%d".formatted(code));
        return format;
    }
}
