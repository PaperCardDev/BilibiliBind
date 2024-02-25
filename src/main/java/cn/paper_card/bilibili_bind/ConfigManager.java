package cn.paper_card.bilibili_bind;

import org.jetbrains.annotations.NotNull;

import java.util.List;

interface ConfigManager {
    @NotNull List<String> getBvid();

    int getAllowMinLevel();

    @NotNull String getReplyFormat();

    @NotNull String getReplay(int code);

}
