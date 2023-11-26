package cn.paper_card.bilibili_bind;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;

public class TestConfirmCodeService {
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
        final ConfirmCodeService service = new ConfirmCodeService(connection);
        service.close();
    }

    @Test
    public void test2() throws SQLException {
        final ConfirmCodeService service = new ConfirmCodeService(connection);

        final UUID uuid = UUID.randomUUID();
        final String name = "Paper" + new Random().nextInt(999999);
        final long uid = new Random().nextLong(99999999999L);
        final String biliName = "PaperCard" + new Random().nextInt(999999);

        // 插入验证码
        final int code = service.getCode(uuid, name, uid, biliName);
        Assert.assertTrue(code > 0);

        // 获取，应该一样的
        final int code2 = service.getCode(uuid, name, uid, biliName);
        Assert.assertEquals(code, code2);


        // 根据UUID查询
        final ConfirmCodeService.Info info2 = service.queryByPlayer(uuid);
        Assert.assertNotNull(info2);
        Assert.assertEquals(uuid, info2.uuid());
        Assert.assertEquals(name, info2.name());
        Assert.assertEquals(code2, info2.code());
        Assert.assertEquals(uid, info2.uid());
        Assert.assertEquals(biliName, info2.biliName());

        // 取出
        final ConfirmCodeService.Info info = service.takeCode(code);
        Assert.assertNotNull(info);
        Assert.assertEquals(uuid, info.uuid());
        Assert.assertEquals(name, info.name());
        Assert.assertEquals(code2, info.code());
        Assert.assertEquals(uid, info.uid());
        Assert.assertEquals(biliName, info.biliName());

        // 再次取出
        final ConfirmCodeService.Info info1 = service.takeCode(code);
        Assert.assertNull(info1);

        service.close();
    }
}
