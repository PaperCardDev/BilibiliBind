package cn.paper_card.bilibili_bind;

import org.jetbrains.annotations.NotNull;

interface ConfigManager {
    @NotNull String getBvid();

    @NotNull String getReplyFormat();

    @NotNull String getReplay(int code);

}
