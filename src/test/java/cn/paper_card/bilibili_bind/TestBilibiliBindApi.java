package cn.paper_card.bilibili_bind;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.*;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

public class TestBilibiliBindApi {
    private MyConnection connection;

    private BilibiliBindApiImpl api;

    @Before
    public void setup() {
        connection = new MyConnection();
        MyConfig config = new MyConfig();

        api = new BilibiliBindApiImpl(connection, connection,
                Logger.getLogger("Test"), config);
    }

    @After
    public void cleanup() throws SQLException {
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
        api.getBindService().addBind(new BilibiliBindApi.BindInfo(uuid, name, uid, "Test", System.currentTimeMillis()));

        // 连接
        final BilibiliBindApi.PreLoginResponse response = api.handlePreLogin(uuid, name);

        Assert.assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, response.result());
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

        final BilibiliBindApi.PreLoginResponse response = api.handlePreLogin(uuid, name);
        Assert.assertNotSame(AsyncPlayerPreLoginEvent.Result.ALLOWED, response.result());
        Assert.assertNotNull(response.kickMessage());

        // 显示消息
        MyUtil.display(Objects.requireNonNull(response.kickMessage()));

        // 发布评论
        System.out.println("请发布评论。。。");

        Thread.sleep(10000);

        final BilibiliBindApi.PreLoginResponse response1 = api.handlePreLogin(uuid, name);
        MyUtil.display(Objects.requireNonNull(response1.kickMessage()));
    }
}
