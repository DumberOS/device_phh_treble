package com.dumbermini.keynavigator;

import java.util.Arrays;
import java.util.Collections;

public final class ScrollNavigationTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ScrollNavigation.Support plain = new ScrollNavigation.Support(false,true,false,false,false,false,false);
        ScrollNavigation.Support list = new ScrollNavigation.Support(true,true,true,true,true,true,false);
        ScrollNavigation.Support legacy = new ScrollNavigation.Support(true,true,false,false,true,true,false);
        ScrollNavigation.Support start = new ScrollNavigation.Support(true,true,false,true,false,true,false);
        ScrollNavigation.Support end = new ScrollNavigation.Support(true,true,true,false,true,false,false);
        ScrollNavigation.Support horizontal = new ScrollNavigation.Support(true,true,false,false,true,true,true);
        ScrollNavigation.Support disabled = new ScrollNavigation.Support(true,false,true,true,true,true,false);
        ScrollNavigation.Support empty = new ScrollNavigation.Support(true,true,false,false,false,false,false);
        ScrollNavigation.Support nonScrollingCollection = new ScrollNavigation.Support(false,true,false,false,false,false,false);
        check(ScrollNavigation.horizontalPager("se.bankgirot.swish", "se.bankgirot.swish:id/view_pager", "x.bLm"),
                "Swish horizontal pager survives obfuscated class names");
        check(ScrollNavigation.horizontalPager("any.app", null, "androidx.viewpager.widget.ViewPager"),
                "AndroidX legacy pager is horizontal");
        check(ScrollNavigation.horizontalPager("any.app", null, "android.support.v4.view.ViewPager"),
                "Support-library legacy pager is horizontal");
        check(ScrollNavigation.horizontalPager("any.app", null, "com.android.internal.widget.ViewPager"),
                "Framework legacy pager is horizontal");
        check(!ScrollNavigation.horizontalPager("any.app", "any.app:id/view_pager", "unknown"),
                "A resource name alone does not determine another app's orientation");
        check(!ScrollNavigation.horizontalPager("se.bankgirot.swish", "se.bankgirot.swish:id/coordinator_layout_history_overview", "android.widget.ScrollView"),
                "Swish history's vertical list remains scrollable");
        check(!ScrollNavigation.horizontalPager("any.app", null, "androidx.viewpager2.widget.ViewPager2"),
                "ViewPager2 may be vertical; require actual axis evidence");
        check(!ScrollNavigation.horizontalPager(null, null, null), "Missing metadata does not classify a pager");
        check(ScrollNavigation.nearest(Arrays.asList(plain,horizontal,plain)) == -1,
                "Swish toolbar has no vertical owner through its tab pager");
        check(ScrollNavigation.nearest(Arrays.asList(horizontal,list,plain)) == 1,
                "Vertical list inside a tab still owns Up/Down");
        check(ScrollNavigation.action(horizontal,true) == ScrollNavigation.Action.NONE,
                "Down must never dispatch FORWARD to a horizontal pager");
        check(ScrollNavigation.action(horizontal,false) == ScrollNavigation.Action.NONE,
                "Up must never dispatch BACKWARD to a horizontal pager");
        check(ScrollNavigation.action(new ScrollNavigation.Support(true,true,true,true,true,true,true),true)
                == ScrollNavigation.Action.DOWN, "Explicit vertical actions override a horizontal hint");
        check(ScrollNavigation.nearest(Collections.emptyList()) == -1, "No ancestors");
        check(ScrollNavigation.nearest(Arrays.asList(plain,plain)) == -1, "Plain containers do not scroll");
        check(ScrollNavigation.nearest(Arrays.asList(plain,list,plain)) == 1, "List containing the highlighted child");
        check(ScrollNavigation.nearest(Arrays.asList(list,plain,legacy)) == 2, "Nearest nested list");
        check(ScrollNavigation.nearest(Arrays.asList(list,horizontal,plain)) == 0, "Skip horizontal carousel");
        check(ScrollNavigation.nearest(Arrays.asList(list,start)) == 1, "Stay with inner list at its upper boundary");
        check(ScrollNavigation.action(start,false) == ScrollNavigation.Action.NONE, "No upward fallback to outer list");
        check(ScrollNavigation.action(end,true) == ScrollNavigation.Action.NONE, "No downward fallback at end");
        check(ScrollNavigation.action(list,true) == ScrollNavigation.Action.DOWN, "Prefer explicit down action");
        check(ScrollNavigation.action(list,false) == ScrollNavigation.Action.UP, "Prefer explicit up action");
        check(ScrollNavigation.action(legacy,true) == ScrollNavigation.Action.FORWARD, "Legacy forward action");
        check(ScrollNavigation.action(legacy,false) == ScrollNavigation.Action.BACKWARD, "Legacy backward action");
        check(ScrollNavigation.nearest(Arrays.asList(list,disabled)) == 1, "Disabled inner list blocks outer scrolling");
        check(ScrollNavigation.action(disabled,true) == ScrollNavigation.Action.NONE, "Disabled list cannot scroll");
        check(ScrollNavigation.nearest(Arrays.asList(list,empty)) == 1, "Recognise a list with no remaining actions");
        check(ScrollNavigation.action(empty,false) == ScrollNavigation.Action.NONE, "Empty list cannot scroll");
        check(ScrollNavigation.nearest(Arrays.asList(list,plain,nonScrollingCollection,plain)) == 0,
                "PostNord: non-scrolling RecyclerView delegates to outer scrolling page");
        check(ScrollNavigation.nearest(Arrays.asList(nonScrollingCollection,plain)) == -1,
                "A collection without scrolling capabilities does not claim scroll keys");
        check(ScrollNavigation.nearest(Arrays.asList(list,nonScrollingCollection,end,plain)) == 2,
                "Real nested list at its lower boundary still prevents scrolling the outer page");
        check(ScrollNavigation.nearest(Arrays.asList(list,nonScrollingCollection,disabled)) == 2,
                "Disabled scrollable ancestor still blocks scrolling an outer page");
        check(SpatialNavigation.closest(Arrays.asList(new SpatialNavigation.Box(0,0,100,40),
                new SpatialNavigation.Box(0,50,100,90), new SpatialNavigation.Box(0,100,100,140)),
                new SpatialNavigation.Box(0,58,100,98)) == 1, "Anchor to a nearby newly visible row");
        check(SpatialNavigation.closest(Collections.emptyList(),new SpatialNavigation.Box(0,0,1,1)) == -1,
                "No fallback target in missing list");
        check(!ScrollNavigation.completeRowHeight(37,53), "Clipped Gmail header is not a complete row");
        check(!ScrollNavigation.completeRowHeight(36,136), "Clipped leading email is not a complete row");
        check(ScrollNavigation.completeRowHeight(136,136), "Fully visible email is complete");
        check(!ScrollNavigation.completeRowHeight(136,0), "Unknown full height falls back");
        check(ScrollNavigation.adjacentRow(0,4,1,100,true) == 2, "Skip clipped header to advance past first complete email");
        check(ScrollNavigation.adjacentRow(4,8,5,100,true) == 6, "Repeated Down advances past clipped leading email");
        check(ScrollNavigation.adjacentRow(4,8,5,100,false) == 3, "Up still advances before the first partial row");
        check(ScrollNavigation.adjacentRow(4,8,-1,100,true) == -1, "No complete row uses ordinary scrolling");
        check(ScrollNavigation.adjacentRow(4,8,9,100,true) == -1, "Invalid complete-row index falls back");
        check(ScrollNavigation.adjacentRow(10,14,10,100,true) == 11, "Down jumps one row with overlap");
        check(ScrollNavigation.adjacentRow(10,14,10,100,false) == 9, "Up jumps one row");
        check(ScrollNavigation.adjacentRow(0,4,0,100,false) == -1, "Top boundary falls back");
        check(ScrollNavigation.adjacentRow(-1,4,-1,100,true) == -1, "Missing row metadata");
        check(ScrollNavigation.adjacentRow(4,4,4,100,true) == -1, "Single tall row keeps ordinary scrolling");
        check(ScrollNavigation.adjacentRow(5,4,5,100,true) == -1, "Reverse row order falls back");
        check(ScrollNavigation.adjacentRow(0,4,0,4,true) == -1, "Invalid row count falls back");
        check(ScrollNavigation.adjacentRow(98,99,98,100,true) == 99, "Last target is in range");
        ScrollNavigation.Settling settling = new ScrollNavigation.Settling(1000, false);
        check(settling.remaining(1000) == 300, "No-event fallback is bounded");
        settling.onEvent(1100, 1100);
        check(settling.remaining(1100) == 120, "First event replaces conservative fallback");
        settling.onEvent(1200, 1200);
        check(settling.remaining(1200) == 120, "Ongoing scroll extends quiet period");
        settling.onEvent(1250, 1400);
        check(settling.remaining(1400) == 0, "Queued final event does not add processing-time delay");
        settling.onEvent(1200, 1400);
        check(settling.remaining(1400) == 0, "Out-of-order event cannot prolong settling");
        settling = new ScrollNavigation.Settling(1000, true);
        check(settling.remaining(1000) == 60, "Immediate jumps retain short fallback");
        settling.onEvent(900, 1010);
        check(settling.remaining(1010) == 50, "Previous scroll event ignored");
        settling.onEvent(1010, 1020);
        check(settling.remaining(1020) == 30, "Immediate event uses original timestamp");
        settling.onEvent(2000, 1020);
        check(settling.remaining(1020) == 40, "Future timestamp is clamped");
        check(ScrollNavigation.needsTouchScroll("ch.protonmail.android", "MailboxList"), "Known Proton list uses touch scroll");
        check(!ScrollNavigation.needsTouchScroll("ch.protonmail.android", "OtherList"), "Other Proton controls are unaffected");
        check(!ScrollNavigation.needsTouchScroll("com.google.android.gm", "MailboxList"), "Other apps retain accessibility scrolling");
        check(!ScrollNavigation.needsTouchScroll(null, null), "Missing target cannot trigger gesture");
        ScrollNavigation.Swipe swipe = ScrollNavigation.swipe(0,100,480,640,true);
        check(swipe.x == 240 && swipe.fromY > swipe.toY, "Down uses upward swipe in list centre");
        check(swipe.toY > 100 && swipe.fromY < 548, "Proton swipe avoids toolbar and bottom floating controls");
        ScrollNavigation.Swipe up = ScrollNavigation.swipe(0,100,480,640,false);
        check(up.fromY == swipe.toY && up.toY == swipe.fromY, "Up reverses the bounded swipe");
        check(ScrollNavigation.swipe(0,0,40,640,true) == null, "Narrow region rejected");
        check(ScrollNavigation.swipe(0,0,480,50,true) == null, "Short region rejected");
        check(ScrollNavigation.swipe(0,100,480,90,true) == null, "Invalid bounds rejected");
        check(ScrollNavigation.bodyScrollOwner("ch.protonmail.android",3,
                Arrays.asList(horizontal,list,plain,plain,legacy)) == 1,
                "Expanded Proton WebView uses outer vertical page, skipping horizontal pager");
        check(ScrollNavigation.bodyScrollOwner("ch.protonmail.android",2,
                Arrays.asList(horizontal,list)) == 1, "Text-only WebView target uses its outer page");
        check(ScrollNavigation.bodyScrollOwner("com.google.android.gm",2,
                Arrays.asList(horizontal,list)) == -1, "Other apps retain nearest scroll owner");
        check(ScrollNavigation.bodyScrollOwner("ch.protonmail.android",-1,
                Arrays.asList(horizontal,list)) == -1, "Mailbox without WebView is unaffected");
        check(ScrollNavigation.bodyScrollOwner("ch.protonmail.android",2,
                Arrays.asList(horizontal,plain)) == -1, "No outer vertical page means normal WebView scrolling");
        check(ScrollNavigation.bodyScrollOwner("ch.protonmail.android",2,
                Arrays.asList(list,end)) == 1, "Boundary on actual outer page remains authoritative");
        check(ScrollNavigation.bodyScrollOwner("ch.protonmail.android",0,
                Arrays.asList(list)) == -1, "Standalone WebView retains own scroll actions");
        System.out.println("Passed " + checks + " scrolling checks");
    }
}
