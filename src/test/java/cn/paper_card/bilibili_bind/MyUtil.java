package cn.paper_card.bilibili_bind;

import cn.paper_card.bilibili_bind.api.BindInfo;
import cn.paper_card.bilibili_bind.api.BindService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.UUID;

public class MyUtil {
    static void removeBind(@NotNull UUID uuid, long uid, @NotNull BindService service) throws Exception {
        // 删除绑定
        {
            final BindInfo bindInfo = service.queryByUuid(uuid);
            if (bindInfo != null) {
                final boolean removed = service.removeBind(bindInfo.uuid(), bindInfo.uid());
                Assert.assertTrue(removed);
            }
        }

        // 删除绑定
        {
            final BindInfo bindInfo = service.queryByUid(uid);
            if (bindInfo != null) {
                final boolean removed = service.removeBind(bindInfo.uuid(), bindInfo.uid());
                Assert.assertTrue(removed);
            }
        }
    }

    static void display(@NotNull Component component) {
        for (Component child : component.children()) {
            if (child instanceof TextComponent text) {
                System.out.print(text.content());
            } else throw new RuntimeException();
        }
        System.out.println();
    }
}
