package cn.paper_card.bilibili_bind;

import cn.paper_card.bilibili_bind.api.BindCodeInfo;
import org.junit.*;

import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;

public class TestBindCodeServiceImpl {

    private MyConnection connection;

    @Before
    public void setup() {
        connection = new MyConnection();
    }

    @After
    public void cleanup() throws SQLException {
        connection.close();
    }

    @Test
    public void test1() throws SQLException {
        final BindCodeServiceImpl service = new BindCodeServiceImpl(connection);

        final UUID uuid = UUID.randomUUID();
        final String name = "Paper99";

        // 生成
        final int code = service.createCode(uuid, name);

        // 取出
        final BindCodeInfo info = service.takeByCode(code);
        Assert.assertNotNull(info);
        Assert.assertEquals(uuid, info.uuid());
        Assert.assertEquals(code, info.code());
        Assert.assertEquals(name, info.name());

        // 再次取出
        final BindCodeInfo info1 = service.takeByCode(code);
        Assert.assertNull(info1);
        final BindCodeInfo info2 = service.takeByUuid(uuid);
        Assert.assertNull(info2);

        final int clean = service.close();
        if (clean > 0) System.out.println(clean);
    }

    @Test
    public void test2() throws SQLException {
        final BindCodeServiceImpl service = new BindCodeServiceImpl(connection);
        final int clean = service.close();
        Assert.assertEquals(-1, clean);
    }

    @Test
    public void test3() throws SQLException {
        final BindCodeServiceImpl service = new BindCodeServiceImpl(connection);

        final UUID uuid = UUID.randomUUID();
        final String name = "Paper99";

        // 生成
        final int code1 = service.createCode(uuid, name);
        final int code2 = service.createCode(uuid, name);

        final var info1 = service.takeByCode(code1);
        Assert.assertNull(info1);


        final var info2 = service.takeByCode(code2);
        Assert.assertNotNull(info2);
        Assert.assertEquals(uuid, info2.uuid());
        Assert.assertEquals(code2, info2.code());
        Assert.assertEquals(name, info2.name());


        service.close();
    }

    @Test
    @Ignore
    public void testMaxAliveTime() throws SQLException, InterruptedException {
        final long maxAliveTime = new Random().nextLong(8000) + 2000;
        final BindCodeServiceImpl service = new BindCodeServiceImpl(connection, maxAliveTime);

        // 生成验证码
        final int code = service.createCode(UUID.randomUUID(), "Paper99");

        // 随机延迟取出
        final long delay = new Random().nextLong(8000) + 2000;

        Thread.sleep(delay);

        final BindCodeInfo info = service.takeByCode(code);
        if (delay < maxAliveTime) {
            Assert.assertNotNull(info);
        } else {
            System.out.println("超时取出");
            Assert.assertNull(info);
        }

        service.close();
    }

}
