package cn.paper_card.bilibili_bind;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

class ConfigDefault implements ConfigManager {
    @Override
    public @NotNull List<String> getBvid() {
        return Collections.emptyList();
    }

    @Override
    public int getAllowMinLevel() {
        return 2;
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
