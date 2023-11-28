package cn.paper_card.bilibili_bind;

import cn.paper_card.bilibili_bind.api.BindInfo;
import cn.paper_card.bilibili_bind.api.PreLoginResponse;
import net.kyori.adventure.text.TextComponent;
import org.junit.*;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class TestBilibiliBindApi {
    private MyConnection connection;

    private BilibiliBindApiImpl api;

    @Before
    public void setup() {
        connection = new MyConnection();
        MyConfig config = new MyConfig();

        api = new BilibiliBindApiImpl(connection,
                connection,
                LoggerFactory.getLogger("Test"),
                config);
    }

    @After
    public void cleanup() {
        connection.close();
        api.close();
    }

    // 绑定了B站，正常进
    @Test
    public void test1() throws Exception {
        final String name = "Paper99";
        final UUID uuid = UUID.randomUUID();
        final long uid = 123456;

        // 删除绑定
        MyUtil.removeBind(uuid, uid, api.getBindService());

        // 添加绑定
        api.getBindService().addBind(new BindInfo(uuid, name, uid, "Test", System.currentTimeMillis()));

        // 连接
        final PreLoginResponse response = api.handlePreLogin(uuid, name);

        Assert.assertTrue(response.allowed());
        Assert.assertNull(response.kickMessage());

        // 删除绑定
        final boolean removed = api.getBindService().removeBind(uuid, uid);
        Assert.assertTrue(removed);
    }


    // 没有绑定，生成验证码
    @Test
    @Ignore
    public void test2() throws Exception {
        final String name = "Paper99";
        final UUID uuid = UUID.randomUUID();
        final long uid = new Random().nextLong(99999999);


        MyUtil.removeBind(uuid, uid, api.getBindService());

        final PreLoginResponse response = api.handlePreLogin(uuid, name);
        Assert.assertFalse(response.allowed());
        Assert.assertNotNull(response.kickMessage());

        // 显示消息
        MyUtil.display(Objects.requireNonNull((TextComponent) response.kickMessage()));

        // 发布评论
        System.out.println("请发布评论。。。");

        Thread.sleep(10000);

        final PreLoginResponse response1 = api.handlePreLogin(uuid, name);
        MyUtil.display(Objects.requireNonNull((TextComponent) response1.kickMessage()));
    }
}
