package cn.paper_card.bilibili_bind;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface BilibiliBindApi {

    // 查询一个玩家的B站UID
    @Nullable Long queryBilibiliUid(@NotNull UUID uuid) throws Exception;

    // 查询B站UID对应的玩家
    @Nullable UUID queryUuid(long bilibiliUid) throws Exception;

    // 设置玩家的Bilibili绑定
    boolean addOrUpdateByUuid(@NotNull UUID uuid, @NotNull String name, long bilibiliUid) throws Exception;
}
