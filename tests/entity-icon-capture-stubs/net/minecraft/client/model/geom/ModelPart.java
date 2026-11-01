package net.minecraft.client.model.geom;

import java.util.List;
import java.util.Map;

public final class ModelPart {
    public float x, y, z, xRot, yRot, zRot, xScale = 1, yScale = 1, zScale = 1;
    public boolean visible = true, skipDraw;
    private final Map<String, ModelPart> children;
    private final boolean hasCubes;
    public ModelPart() { this(true, Map.of()); }
    public ModelPart(Map<String, ModelPart> children) { this(false, children); }
    public ModelPart(boolean hasCubes, Map<String, ModelPart> children) {
        this.hasCubes = hasCubes; this.children = children;
    }
    public boolean hasChild(String name) { return children.containsKey(name); }
    public ModelPart getChild(String name) { return children.get(name); }
    public boolean isEmpty() { return !hasCubes; }
    public List<ModelPart> getAllParts() { return List.copyOf(children.values()); }
}
