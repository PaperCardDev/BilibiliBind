package cn.paper_card.bilibili_bind;

import org.jetbrains.annotations.NotNull;

import java.util.List;

class ConfigManagerImpl implements ConfigManager {

    private final @NotNull ThePlugin plugin;
    private final @NotNull ConfigDefault aDefault;

    private final static String PATH_ALLOW_MIN_LEVEL = "allow-min-level";
    private final static String PATH_REPLY_FORMAT = "reply-format";

    private final static String PATH_BVID = "bvid";

    ConfigManagerImpl(@NotNull ThePlugin plugin) {
        this.plugin = plugin;
        this.aDefault = new ConfigDefault();
    }

    @Override
    public @NotNull List<String> getBvid() {
        return this.plugin.getConfig().getStringList(PATH_BVID);
    }

    private void setBvid(@NotNull List<String> bvid) {
        this.plugin.getConfig().set(PATH_BVID, bvid);
    }


    @Override
    public int getAllowMinLevel() {
        return this.plugin.getConfig().getInt(PATH_ALLOW_MIN_LEVEL, this.aDefault.getAllowMinLevel());
    }

    private void setAllowMinLevel(int v) {
        this.plugin.getConfig().set(PATH_ALLOW_MIN_LEVEL, v);
    }

    @Override
    public @NotNull String getReplyFormat() {
        return this.plugin.getConfig().getString(PATH_REPLY_FORMAT, this.aDefault.getReplyFormat());
    }

    private void setReplyFormat(@NotNull String v) {
        this.plugin.getConfig().set(PATH_REPLY_FORMAT, v);
    }

    @Override
    public @NotNull String getReplay(int code) {
        String format = this.getReplyFormat();
        format = format.replace("%code%", "%d".formatted(code));
        return format;
    }

    void setDefaults() {
        this.setAllowMinLevel(this.getAllowMinLevel());
        this.setReplyFormat(this.getReplyFormat());
        this.setBvid(this.getBvid());
    }

    void save() {
        this.plugin.saveConfig();
    }

    void reload() {
        this.plugin.reloadConfig();
    }
}
