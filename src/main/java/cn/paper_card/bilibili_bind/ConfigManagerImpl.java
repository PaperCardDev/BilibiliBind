package cn.paper_card.bilibili_bind;

import org.jetbrains.annotations.NotNull;

class ConfigManagerImpl implements ConfigManager {

    private final @NotNull ThePlugin plugin;
    private final @NotNull ConfigDefault aDefault;

    ConfigManagerImpl(@NotNull ThePlugin plugin) {
        this.plugin = plugin;
        this.aDefault = new ConfigDefault();
    }

    @Override
    public @NotNull String getBvid() {
        return this.plugin.getConfig().getString("bvid", this.aDefault.getBvid());
    }

    @Override
    public @NotNull String getReplyFormat() {
        return this.plugin.getConfig().getString("reply-format", this.aDefault.getReplyFormat());
    }

    @Override
    public @NotNull String getReplay(int code) {
        return this.aDefault.getReplay(code);
    }
}
