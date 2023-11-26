package cn.paper_card.bilibili_bind;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestBilibiliUtil {

    final long aid = 620947279;

    final long uid = 391939252;

    @Test
    public void test1() throws Exception {
        final BilibiliUtil util = new BilibiliUtil();

        final String bvid = "BV1rb4y1g7YP";

        final BilibiliUtil.VideoInfo videoInfo = util.requestVideoByBvid(bvid);

        Assert.assertNotNull(videoInfo);
        Assert.assertEquals(bvid, videoInfo.bvid());
        Assert.assertEquals(aid, videoInfo.aid());
        Assert.assertEquals(uid, videoInfo.ownerId());

//        System.out.println(videoInfo);
    }

    @Test
    public void test2() throws Exception {
        final BilibiliUtil util = new BilibiliUtil();

        final List<BilibiliUtil.Reply> replies = util.requestLatestReplies(aid);

        Assert.assertFalse(replies.isEmpty());

//        System.out.println(replies);
        for (BilibiliUtil.Reply reply : replies) {
            System.out.println(reply);
        }
    }
}
