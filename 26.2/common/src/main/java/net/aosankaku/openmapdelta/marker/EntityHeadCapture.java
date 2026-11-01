// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Supplier;

/** Safe, deliberately narrow helpers for extracting a head from the renderer's submitted model. */
final class EntityHeadCapture {
    private EntityHeadCapture() {}

    /** Finds a head only when its root transform can be kept without guessing at nested parents. */
    static ModelPart selectHead(Model<?> model) {
        if (model == null) return null;
        ModelPart root = model.root();
        if (model instanceof HeadedModel headed) {
            ModelPart head = headed.getHead();
            if (head != null && head != root && root.hasChild("head") && root.getChild("head") == head) return head;
        }
        return root.hasChild("head") ? root.getChild("head") : null;
    }

    static PoseSnapshot snapshot(Model<?> model) {
        List<PartPose> poses = new ArrayList<>();
        for (ModelPart part : model.allParts()) poses.add(PartPose.capture(part));
        return new PoseSnapshot(poses);
    }

    /** Restores all mutable model-part fields, including scale/visibility that storePose omits. */
    static void restore(Model<?> model, PoseSnapshot snapshot) {
        snapshot.restore();
    }

    /** Keeps the first pose seen for each submitted model identity and restores all of them together. */
    static final class PoseRestorations {
        private final IdentityHashMap<Model<?>, PoseSnapshot> poses = new IdentityHashMap<>();

        void capture(Model<?> model) {
            if (model != null) poses.computeIfAbsent(model, EntityHeadCapture::snapshot);
        }

        void restore() {
            poses.forEach(EntityHeadCapture::restore);
        }
    }

    static <S> void withAnimatedPose(Model<S> model, S state, Runnable render) {
        PoseSnapshot original = snapshot(model);
        withAnimatedPose(model, state, original, render);
    }

    static <S> void withAnimatedPose(Model<S> model, S state, PoseSnapshot original, Runnable render) {
        try {
            original.restore();
            model.setupAnim(state);
            render.run();
        } finally {
            restore(model, original);
        }
    }

    static <S, R> R inspectAnimatedPose(Model<S> model, S state, Supplier<R> inspection) {
        return inspectAnimatedPose(model, state, snapshot(model), inspection);
    }

    static <S, R> R inspectAnimatedPose(Model<S> model, S state, PoseSnapshot original,
                                         Supplier<R> inspection) {
        try {
            original.restore();
            model.setupAnim(state);
            return inspection.get();
        } finally {
            restore(model, original);
        }
    }

    static final class PoseSnapshot {
        private final List<PartPose> poses;
        private PoseSnapshot(List<PartPose> poses) { this.poses = poses; }
        void restore() { for (PartPose pose : poses) pose.restore(); }
    }

    private record PartPose(ModelPart part, float x, float y, float z, float xRot, float yRot, float zRot,
                            float xScale, float yScale, float zScale, boolean visible, boolean skipDraw) {
        static PartPose capture(ModelPart part) {
            return new PartPose(part, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale, part.visible, part.skipDraw);
        }
        void restore() {
            part.x = x; part.y = y; part.z = z;
            part.xRot = xRot; part.yRot = yRot; part.zRot = zRot;
            part.xScale = xScale; part.yScale = yScale; part.zScale = zScale;
            part.visible = visible; part.skipDraw = skipDraw;
        }
    }
}
