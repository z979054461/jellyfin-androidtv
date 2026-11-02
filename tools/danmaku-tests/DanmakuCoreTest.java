import org.jellyfin.androidtv.ui.playback.danmaku.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dependency-free regression suite. Run using tools/danmaku-tests/run.sh. */
public class DanmakuCoreTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Check " + checks + " failed");
    }
    private static List<DanmakuComment> parse(String xml) throws IOException {
        return DanmakuParser.parse(xml.getBytes(StandardCharsets.UTF_8));
    }
    private static void rejects(String xml) throws Exception {
        try { parse(xml); throw new AssertionError("Accepted unsafe/invalid XML"); }
        catch (IOException expected) { checks++; }
    }
    public static void main(String[] args) throws Exception {
        List<DanmakuComment> parsed = parse("<i><d p='2,1,25,16777215'>hello &amp; world</d><d p='1,5,25,0'>顶</d><d p='NaN,1,25,0'>bad</d><d p='1,7,25,0'>unsupported</d><d p='1,4,25,99999999'>bad color</d></i>");
        check(parsed.size() == 2);
        check(parsed.get(0).timeMs == 1000 && parsed.get(0).mode == 5);
        check(parsed.get(1).text.equals("hello & world") && parsed.get(1).color == 0xffffffff);
        rejects("<!DOCTYPE i [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><i><d p='0,1,25,0'>&x;</d></i>");
        rejects("<!DOCTYPE i SYSTEM 'https://example.invalid'><i/>");
        rejects("<i><d></i>");
        rejects("<i>" + String.join("", Collections.nCopies(20, "<x>")) + "</i>");
        check(parse("<i><d p='0,1,25,0'>" + String.join("", Collections.nCopies(500, "a")) + "</d></i>").get(0).text.length() == 300);
        try { DanmakuParser.parse(new byte[DanmakuParser.MAX_BYTES + 1]); throw new AssertionError(); } catch (IOException expected) { checks++; }
        try { DanmakuParser.parse(new byte[]{(byte) 0xff}); throw new AssertionError(); } catch (IOException expected) { checks++; }
        rejects("<i>" + String.join("", Collections.nCopies(DanmakuParser.MAX_COMMENTS + 1, "<d p='0,1,25,0'>a</d>")) + "</i>");
        List<DanmakuComment> comments = Arrays.asList(new DanmakuComment(0,1,-1,"aaa"),new DanmakuComment(100,1,-1,"bbb"),new DanmakuComment(4000,1,-1,"ccc"));
        DanmakuTimeline timeline = new DanmakuTimeline(comments,1000,50,1,8000,t -> t.length()*20f);
        check(timeline.frame(-1).isEmpty());
        check(timeline.frame(0).get(0).x == 1000f);
        check(timeline.frame(200).size() == 1); // crowd dropped, not overlapping
        float beforeSeek = timeline.frame(1000).get(0).x;
        timeline.frame(7999);
        check(timeline.frame(1000).get(0).x == beforeSeek); // backward seek deterministic
        check(timeline.frame(1000).get(0).x == beforeSeek); // pause frozen
        check(timeline.frame(100000).isEmpty());
        check(timeline.frame(8000).size() == 1); // first item expires exactly at duration
        List<DanmakuComment> mixed = Arrays.asList(new DanmakuComment(0,5,-1,"top"),new DanmakuComment(0,4,-1,"bottom"));
        DanmakuTimeline fixed = new DanmakuTimeline(mixed,1000,50,4,8000,t -> 100f);
        check(fixed.frame(100).get(0).lane == 0 && fixed.frame(100).get(1).lane == 3);
        check(fixed.frame(100).get(0).x == 450f);
        DanmakuTimeline mixedLane = new DanmakuTimeline(Arrays.asList(new DanmakuComment(0,1,-1,"scroll"), new DanmakuComment(100,5,-1,"fixed")), 1000,50,1,8000,t -> 100f);
        check(mixedLane.frame(500).size() == 1);
        check(parse("\uFEFF<i><d p='0,1,25,0'>ok</d><d p='Infinity,1,25,0'>bad</d></i>").size() == 1);
        try { new DanmakuTimeline(comments, 0,50,1,8000,t -> 20f); throw new AssertionError(); } catch (IllegalArgumentException expected) { checks++; }
        Random random = new Random(7);
        List<DanmakuComment> crowded = new ArrayList<>();
        for (int i=0; i<1000; i++) crowded.add(new DanmakuComment(i*80,1,-1,String.join("",Collections.nCopies(random.nextInt(50)+1,"x"))));
        DanmakuTimeline collision = new DanmakuTimeline(crowded,1000,50,4,8000,t -> t.length()*20f);
        for (int time=0; time<90000; time+=25) {
            List<DanmakuTimeline.Placement> placements = collision.frame(time);
            for (int a=0; a<placements.size(); a++) for (int b=a+1; b<placements.size(); b++) {
                DanmakuTimeline.Placement left=placements.get(a), right=placements.get(b);
                if (left.lane != right.lane) continue;
                check(left.x + left.comment.text.length()*20 <= right.x || right.x + right.comment.text.length()*20 <= left.x);
            }
        }
        System.out.println("Danmaku core: " + checks + " checks passed");
    }
}
