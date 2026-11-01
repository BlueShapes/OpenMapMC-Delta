package net.aosankaku.openmapdelta.marker;

import java.util.Map;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

public final class EntityHeadCaptureTest {
    private static int checks;
    public static void main(String[] args) {
        headedModelHeadWinsOverNameFallback();
        directHeadChildIsSupportedAndUnknownHierarchyFallsBack();
        poseIsRestoredAfterAnimationAndFailure();
        animatedHeadBoundsAreMeasuredAfterAnimationAndRestored();
        submissionPoseRestorationTracksDistinctModelsOnce();
        System.out.println("Entity head capture checks passed (" + checks + ").");
    }
    private static void headedModelHeadWinsOverNameFallback() {
        ModelPart designated = new ModelPart();
        TestModel model = new TestModel(new ModelPart(Map.of("head", designated)), designated);
        check(EntityHeadCapture.selectHead(model) == designated,
                "HeadedModel provides the preferred safe head part");
        ModelPart nested = new ModelPart();
        TestModel unsafe = new TestModel(new ModelPart(Map.of("body", new ModelPart(Map.of("head", nested)))), nested);
        check(EntityHeadCapture.selectHead(unsafe) == null,
                "nested HeadedModel head is rejected when parent transforms cannot be preserved");
    }
    private static void directHeadChildIsSupportedAndUnknownHierarchyFallsBack() {
        ModelPart named = new ModelPart(false, Map.of("nose", new ModelPart()));
        TestModel direct = new TestModel(new ModelPart(Map.of("head", named)), null);
        check(EntityHeadCapture.selectHead(direct) == named, "direct head child is supported");
        check(named.isEmpty(), "a head containing only child parts may have no own cubes");
        check(EntityHeadCapture.selectHead(direct) == named, "children-only head remains eligible");
        TestModel nested = new TestModel(new ModelPart(Map.of("body", new ModelPart(Map.of("head", named)))), null);
        check(EntityHeadCapture.selectHead(nested) == null, "unknown nested hierarchy falls back safely");
        check(EntityHeadCapture.selectHead(new TestModel(new ModelPart(), null)) == null,
                "model with no head falls back safely");
    }
    private static void poseIsRestoredAfterAnimationAndFailure() {
        ModelPart root = new ModelPart(), head = new ModelPart();
        set(root, 2, 3, 4, .1F, .25F, .4F, 1.5F, 1.25F, .8F, true, true);
        set(head, 4, 5, 6, .2F, .3F, .5F, .9F, .75F, 1.1F, false, true);
        TestModel model = new TestModel(new ModelPart(Map.of("head", head)), head);
        ModelPart actualRoot = model.root();
        set(actualRoot, root.x, root.y, root.z, root.xRot, root.yRot, root.zRot,
                root.xScale, root.yScale, root.zScale, root.visible, root.skipDraw);
        set(model.head, head.x, head.y, head.z, head.xRot, head.yRot, head.zRot,
                head.xScale, head.yScale, head.zScale, head.visible, head.skipDraw);
        EntityHeadCapture.withAnimatedPose(model, "state", () -> {
            check(model.head.x == 9 && model.head.yScale == 2 && model.head.visible, "animation runs while head is captured");
        });
        assertPose(actualRoot, model.head, "successful capture restores every part pose field");
        boolean failed = false;
        try { EntityHeadCapture.withAnimatedPose(model, "state", () -> { throw new IllegalStateException("fixture"); }); }
        catch (IllegalStateException expected) { failed = true; }
        check(failed, "capture failure remains visible");
        assertPose(actualRoot, model.head, "failed capture restores every part pose field");
    }
    private static void animatedHeadBoundsAreMeasuredAfterAnimationAndRestored() {
        ModelPart head = new ModelPart();
        set(head, 4, 5, 6, .2F, .3F, .5F, .9F, .75F, 1.1F, false, true);
        TestModel model = new TestModel(new ModelPart(Map.of("head", head)), head);
        float[] bounds = EntityHeadCapture.inspectAnimatedPose(model, "animated-state", () ->
                new float[]{model.head.x - model.head.xScale, model.head.x + model.head.xScale});
        check(bounds[0] == 4.0F && bounds[1] == 14.0F,
                "head bounds include the animated head position and scale used by rendering");
        check(model.head.x == 4 && model.head.xScale == .9F && !model.head.visible && model.head.skipDraw,
                "animated-bounds inspection restores the original model pose afterward");
    }
    private static void submissionPoseRestorationTracksDistinctModelsOnce() {
        ModelPart firstHead = new ModelPart();
        ModelPart secondHead = new ModelPart();
        TestModel first = new TestModel(new ModelPart(Map.of("head", firstHead)), firstHead);
        TestModel second = new TestModel(new ModelPart(Map.of("head", secondHead)), secondHead);
        set(first.root(), 1, 2, 3, .1F, .2F, .3F, 1.1F, 1.2F, 1.3F, true, false);
        set(firstHead, 4, 5, 6, .4F, .5F, .6F, .7F, .8F, .9F, true, false);
        set(second.root(), 7, 8, 9, .7F, .8F, .9F, 1.4F, 1.5F, 1.6F, false, true);
        set(secondHead, 10, 11, 12, 1.0F, 1.1F, 1.2F, .6F, .5F, .4F, false, true);

        EntityHeadCapture.PoseRestorations restorations = new EntityHeadCapture.PoseRestorations();
        restorations.capture(first);
        restorations.capture(second);
        set(first.root(), 20, 21, 22, 2, 3, 4, 2, 3, 4, false, true);
        set(firstHead, 23, 24, 25, 5, 6, 7, 5, 6, 7, false, true);
        set(second.root(), 30, 31, 32, 3, 4, 5, 3, 4, 5, true, false);
        set(secondHead, 33, 34, 35, 6, 7, 8, 6, 7, 8, true, false);
        restorations.capture(first);
        restorations.restore();

        check(first.root().x == 1 && first.root().xScale == 1.1F && first.root().visible
                        && firstHead.x == 4 && firstHead.xScale == .7F && !firstHead.skipDraw,
                "first submission snapshot restores the renderer's original model pose");
        check(second.root().x == 7 && second.root().xScale == 1.4F && !second.root().visible
                        && secondHead.x == 10 && secondHead.xScale == .6F && secondHead.skipDraw,
                "separate layer or variant model snapshots restore independently");
    }
    private static void assertPose(ModelPart root, ModelPart head, String message) {
        check(root.x == 2 && root.y == 3 && root.z == 4 && root.xRot == .1F && root.yRot == .25F && root.zRot == .4F
                        && root.xScale == 1.5F && root.yScale == 1.25F && root.zScale == .8F && root.visible && root.skipDraw,
                message + " (all root transforms, scales, and draw flags)");
        check(head.x == 4 && head.y == 5 && head.z == 6 && head.xRot == .2F && head.yRot == .3F && head.zRot == .5F
                        && head.xScale == .9F && head.yScale == .75F && head.zScale == 1.1F && !head.visible && head.skipDraw,
                message + " (all head transforms, scales, and draw flags)");
    }
    private static final class TestModel extends Model<String> implements HeadedModel {
        private final ModelPart head;
        TestModel(ModelPart root, ModelPart preferred) {
            super(root); this.head = preferred;
        }
        @Override public ModelPart getHead() { return head; }
        @Override public void setupAnim(String state) {
            set(root(), 11, 12, 13, 1, 2, 3, 4, 5, 6, false, false);
            if (head != null) set(head, 9, 10, 11, 2, 3, 4, 5, 2, 6, true, false);
            else if (root().hasChild("head")) {
                ModelPart child = root().getChild("head");
                set(child, 9, 10, 11, 2, 3, 4, 5, 2, 6, true, false);
            }
        }
    }
    private static void set(ModelPart part, float x, float y, float z, float xRot, float yRot, float zRot,
                            float xScale, float yScale, float zScale, boolean visible, boolean skipDraw) {
        part.x = x; part.y = y; part.z = z; part.xRot = xRot; part.yRot = yRot; part.zRot = zRot;
        part.xScale = xScale; part.yScale = yScale; part.zScale = zScale;
        part.visible = visible; part.skipDraw = skipDraw;
    }
    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
}
