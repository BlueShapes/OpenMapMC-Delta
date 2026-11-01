package net.minecraft.client.model;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;

public abstract class Model<S> {
    private final ModelPart root;
    protected Model(ModelPart root) { this.root = root; }
    public ModelPart root() { return root; }
    public List<ModelPart> allParts() {
        List<ModelPart> result = new ArrayList<>();
        collect(root, result);
        return result;
    }
    private static void collect(ModelPart part, List<ModelPart> result) {
        result.add(part);
        for (ModelPart child : part.getAllParts()) collect(child, result);
    }
    public void setupAnim(S state) {}
}
