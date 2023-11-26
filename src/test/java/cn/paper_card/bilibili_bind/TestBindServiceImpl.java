package cn.paper_card.bilibili_bind;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;

public class TestBindServiceImpl {

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
    public void test1() throws SQLException, BilibiliBindApi.UidHasBeenBindException, BilibiliBindApi.AlreadyBindException {

        final BindServiceImpl bindService = new BindServiceImpl(connection);

        final BilibiliBindApi.BindInfo testInfo = new BilibiliBindApi.BindInfo(
                UUID.randomUUID(),
                "Paper99",
                123456,
                "Test",
                System.currentTimeMillis()
        );

        // 先删除，确保没有重复
        bindService.removeBind(testInfo.uuid(), testInfo.uid());

        // 添加
        bindService.addBind(testInfo);

        // 按UUID查询
        final var info1 = bindService.queryByUuid(testInfo.uuid());
        Assert.assertEquals(testInfo, info1);

        // 按UID查询
        final var info2 = bindService.queryByUid(testInfo.uid());
        Assert.assertEquals(testInfo, info2);

        // 删除
        final boolean removed = bindService.removeBind(testInfo.uuid(), testInfo.uid());
        Assert.assertTrue(removed);

        bindService.close();
    }

    @Test
    public void test2() throws SQLException {
        final BindServiceImpl service = new BindServiceImpl(this.connection);
        service.close();
    }

    @Test
    public void testAlreadyBind() throws SQLException, BilibiliBindApi.UidHasBeenBindException, BilibiliBindApi.AlreadyBindException {
        final BindServiceImpl service = new BindServiceImpl(this.connection);

        final BilibiliBindApi.BindInfo test = new BilibiliBindApi.BindInfo(UUID.randomUUID(),
                "Paper99", new Random().nextLong(99999999), "Test", System.currentTimeMillis());

        // 先删除
        {
            final BilibiliBindApi.BindInfo tmp = service.queryByUuid(test.uuid());
            if (tmp != null) {
                final boolean removed = service.removeBind(tmp.uuid(), tmp.uid());
                Assert.assertTrue(removed);
            }

            final BilibiliBindApi.BindInfo tmp2 = service.queryByUid(test.uid());
            if (tmp2 != null) {
                final boolean removed = service.removeBind(tmp2.uuid(), tmp2.uid());
                Assert.assertTrue(removed);
            }
        }


        // 添加
        service.addBind(test);

        // 重复添加
        try {
            service.addBind(test);
            throw new RuntimeException("没有异常！");
        } catch (BilibiliBindApi.AlreadyBindException e) {
            final BilibiliBindApi.BindInfo bindInfo = e.getBindInfo();
            Assert.assertEquals(test, bindInfo);
        }

        // 删除
        final boolean removed = service.removeBind(test.uuid(), test.uid());
        Assert.assertTrue(removed);

        service.close();
    }
}
