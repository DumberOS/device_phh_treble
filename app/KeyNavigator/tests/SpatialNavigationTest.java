package com.dumbermini.keynavigator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SpatialNavigationTest {
    private static int checks;
    private static SpatialNavigation.Box box(int l, int t, int r, int b) {
        return new SpatialNavigation.Box(l, t, r, b);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        List<SpatialNavigation.Box> grid = Arrays.asList(box(0,0,40,40), box(60,0,100,40),
                box(0,60,40,100), box(60,60,100,100));
        check(SpatialNavigation.initial(grid) == 0, "Initial top-left target");
        check(SpatialNavigation.next(grid,0,SpatialNavigation.Direction.RIGHT)==1,"Right");
        check(SpatialNavigation.next(grid,0,SpatialNavigation.Direction.DOWN)==2,"Down");
        check(SpatialNavigation.next(grid,3,SpatialNavigation.Direction.LEFT)==2,"Left");
        check(SpatialNavigation.next(grid,3,SpatialNavigation.Direction.UP)==1,"Up");
        check(SpatialNavigation.next(grid,0,SpatialNavigation.Direction.LEFT)==0,"No left wrap");
        check(SpatialNavigation.next(grid,3,SpatialNavigation.Direction.DOWN)==3,"No bottom wrap");
        check(SpatialNavigation.next(grid,-1,SpatialNavigation.Direction.DOWN)==0,"Missing selection initializes");
        check(SpatialNavigation.initial(Collections.emptyList())==-1,"Empty screen");
        check(SpatialNavigation.next(Collections.emptyList(),-1,SpatialNavigation.Direction.UP)==-1,"Empty navigation");
        check(SpatialNavigation.next(Collections.singletonList(box(0,0,10,10)),0,
                SpatialNavigation.Direction.RIGHT)==0,"Single target");
        List<SpatialNavigation.Box> aligned = Arrays.asList(box(0,0,40,40),box(60,60,100,100),box(180,0,220,40));
        check(SpatialNavigation.next(aligned,0,SpatialNavigation.Direction.RIGHT)==2,"Prefer aligned row");
        check(SpatialNavigation.next(Arrays.asList(box(0,0,40,40),box(60,60,100,100)),0,
                SpatialNavigation.Direction.RIGHT)==1,"Diagonal fallback");
        check(SpatialNavigation.next(Arrays.asList(box(0,0,40,40),box(0,0,40,40),box(60,0,100,40)),0,
                SpatialNavigation.Direction.RIGHT)==2,"Duplicate bounds cannot trap focus");
        check(SpatialNavigation.initial(Arrays.asList(box(0,0,0,10),box(2,2,10,10)))==1,"Empty bounds ignored");
        // Bounds measured on Swish's home screen: icon, main controls, then bottom tabs.
        List<SpatialNavigation.Box> swish = Arrays.asList(box(21,58,327,163),box(399,48,462,111),
                box(0,237,480,300),box(26,461,234,535),box(245,461,454,535),
                box(122,556,234,640),box(245,556,357,640),box(368,556,480,640));
        check(SpatialNavigation.next(swish,3,SpatialNavigation.Direction.RIGHT)==4,"Swish main controls");
        check(SpatialNavigation.next(swish,4,SpatialNavigation.Direction.DOWN)==6,"Swish control to tab");
        check(SpatialNavigation.next(swish,6,SpatialNavigation.Direction.RIGHT)==7,"Swish tab row");
        check(SpatialNavigation.next(swish,7,SpatialNavigation.Direction.RIGHT)==7,"Swish row edge");
        check(SpatialNavigation.next(swish,1,SpatialNavigation.Direction.LEFT)==0,"Swish header controls");
        List<SpatialNavigation.Box> textPage = Arrays.asList(box(8,37,66,111), box(0,111,480,566),
                box(120,566,240,640));
        check(SpatialNavigation.next(textPage,0,SpatialNavigation.Direction.DOWN)==1,
                "Text-only scroll region is reachable from toolbar");
        check(SpatialNavigation.next(textPage,2,SpatialNavigation.Direction.UP)==1,
                "Text-only scroll region is reachable from bottom navigation");
        check(SpatialNavigation.closest(Collections.singletonList(textPage.get(1)),box(406,327,480,401))==0,
                "Lost row selection can anchor to its now text-only scroll region");
        List<SpatialNavigation.Box> rows = Arrays.asList(box(0,0,100,40), box(0,50,100,90),
                box(0,100,100,140), box(0,150,100,190));
        check(!SpatialNavigation.nearVerticalEdge(rows,0,SpatialNavigation.Direction.DOWN), "Top row moves before scrolling down");
        check(!SpatialNavigation.nearVerticalEdge(rows,1,SpatialNavigation.Direction.DOWN), "Two rows ahead still moves");
        check(SpatialNavigation.nearVerticalEdge(rows,2,SpatialNavigation.Direction.DOWN), "Penultimate row scrolls down");
        check(SpatialNavigation.nearVerticalEdge(rows,3,SpatialNavigation.Direction.DOWN), "Last row scrolls down");
        check(SpatialNavigation.nearVerticalEdge(rows,1,SpatialNavigation.Direction.UP), "Second row scrolls up");
        check(SpatialNavigation.nearVerticalEdge(rows,0,SpatialNavigation.Direction.UP), "First row scrolls up");
        check(!SpatialNavigation.nearVerticalEdge(rows,2,SpatialNavigation.Direction.UP), "Two rows above still moves");
        check(!SpatialNavigation.nearVerticalEdge(rows,2,SpatialNavigation.Direction.LEFT), "Horizontal movement does not scroll");
        check(!SpatialNavigation.nearVerticalEdge(rows,-1,SpatialNavigation.Direction.DOWN), "Missing selection does not scroll");
        check(SpatialNavigation.nearVerticalEdge(Collections.singletonList(textPage.get(1)),0,
                SpatialNavigation.Direction.DOWN), "Text-only region scrolls down immediately");
        check(SpatialNavigation.nearVerticalEdge(Collections.singletonList(textPage.get(1)),0,
                SpatialNavigation.Direction.UP), "Text-only region scrolls up immediately");
        check(!SpatialNavigation.nearVerticalEdge(Collections.emptyList(),0,SpatialNavigation.Direction.DOWN), "Empty list does not scroll");
        check(SpatialNavigation.nearVerticalEdge(grid,0,SpatialNavigation.Direction.DOWN), "Other columns do not delay edge scrolling");
        check(SpatialNavigation.next(rows,2,SpatialNavigation.Direction.DOWN)==3, "Movement-only mode still visits last row");
        SpatialNavigation.Box viewport = box(0,0,480,640);
        SpatialNavigation.Box gmailVisible = SpatialNavigation.withoutEdgeBars(viewport,
                Collections.singletonList(box(0,577,480,640)));
        check(gmailVisible.same(box(0,0,480,577)), "Gmail tab bar clips list viewport");
        check(!SpatialNavigation.rowCenterVisible(box(11,564,469,640),gmailVisible),
                "Row behind Gmail tabs does not postpone scrolling");
        check(SpatialNavigation.rowCenterVisible(box(11,426,469,562),gmailVisible),
                "Last readable Gmail row remains the boundary");
        check(SpatialNavigation.withoutEdgeBars(viewport,Collections.singletonList(box(251,482,448,556))).same(viewport),
                "Floating Compose button does not truncate the list");
        check(SpatialNavigation.withoutEdgeBars(viewport,Collections.singletonList(box(0,200,480,250))).same(viewport),
                "Middle strip is not an edge bar");
        check(SpatialNavigation.withoutEdgeBars(viewport,Collections.singletonList(box(0,0,480,640))).same(viewport),
                "Full-screen container is not a tab bar");
        check(SpatialNavigation.withoutEdgeBars(viewport,Collections.singletonList(box(0,0,480,60))).top == 60,
                "Fixed top tabs clip the upper edge");
        check(SpatialNavigation.nearVerticalEdge(Arrays.asList(box(11,12,469,148),box(11,150,469,286),
                box(11,288,469,424),box(11,426,469,562)),2,SpatialNavigation.Direction.DOWN),
                "Gmail down scrolls before entering the last readable row");
        System.out.println("Passed " + checks + " spatial navigation checks");
    }
}
