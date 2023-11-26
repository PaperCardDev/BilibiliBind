package cn.paper_card.bilibili_bind;

import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface BilibiliBindApi {

    record BindCodeInfo(
            int code,
            UUID uuid,
            String name,
            long time
    ) {
    }

    record BindInfo(
            UUID uuid,
            String name,
            long uid,
            String remark,
            long time
    ) {
    }

    class AlreadyBindException extends Exception {
        private final @NotNull BindInfo bindInfo;

        AlreadyBindException(@NotNull BindInfo bindInfo) {
            this.bindInfo = bindInfo;
        }

        @NotNull BindInfo getBindInfo() {
            return this.bindInfo;
        }

        @Override
        public String getMessage() {
            return "%s (%s) 已经绑定了B站UID：%d".formatted(bindInfo.name(), bindInfo.uuid(), bindInfo.uid());
        }
    }

    class UidHasBeenBindException extends Exception {
        private final @NotNull BindInfo bindInfo;

        UidHasBeenBindException(@NotNull BindInfo bindInfo) {
            this.bindInfo = bindInfo;
        }

        @NotNull BindInfo getBindInfo() {
            return this.bindInfo;
        }
    }


    interface BindService {
        // 添加绑定
        void addBind(@NotNull BindInfo info) throws Exception;

        // 删除绑定
        boolean removeBind(@NotNull UUID uuid, long uid) throws Exception;

        @Nullable BindInfo queryByUuid(@NotNull UUID uuid) throws Exception;

        @Nullable BindInfo queryByUid(long uid) throws Exception;
    }

    interface BindCodeService {
        int createCode(@NotNull UUID uuid, @NotNull String name) throws Exception;

        @Nullable BindCodeInfo takeByCode(int code) throws Exception;

        @Nullable BindCodeInfo takeByUuid(@NotNull UUID uuid) throws Exception;

        int cleanOutdated() throws Exception;
    }

    interface PreLoginResponse {
        @NotNull AsyncPlayerPreLoginEvent.Result result();

        @Nullable Component kickMessage();
    }


    @NotNull BindService getBindService();

    @NotNull BindCodeService getBindCodeService();

    @NotNull PreLoginResponse handlePreLogin(@NotNull UUID uuid, @NotNull String name);
}
