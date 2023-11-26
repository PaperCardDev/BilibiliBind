package cn.paper_card.bilibili_bind;

import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record PreLoginResponseImpl(
        @NotNull AsyncPlayerPreLoginEvent.Result result,
        @Nullable Component kickMessage

) implements BilibiliBindApi.PreLoginResponse {
}
