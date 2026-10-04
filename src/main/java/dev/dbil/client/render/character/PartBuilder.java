package dev.dbil.client.render.character;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Small voxel geometry DSL on top of Minecraft's mesh builders. Pixel units, head/body local space:
 * y grows downwards, the face looks towards -Z and the character's right side is -X.
 * Each top-level child becomes one {@link Piece} so it can be tinted and animated on its own.
 */
public final class PartBuilder {
    public record Piece(String name, ModelPart part, int slot, boolean sway) {}
    public record Segment(float size, float length) {}

    private final MeshDefinition mesh = new MeshDefinition();
    private final List<Spec> specs = new ArrayList<>();
    private final int textureWidth, textureHeight;
    private int counter;

    private record Spec(String name, int slot, boolean sway) {}

    public PartBuilder(int textureWidth, int textureHeight) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    public static Segment seg(float size, float length) { return new Segment(size, length); }

    /** A plain box. */
    public PartBuilder box(int slot, float x, float y, float z, float w, float h, float d) {
        return box(slot, 0, 0, x, y, z, w, h, d, 0);
    }

    public PartBuilder box(int slot, int u, int v, float x, float y, float z, float w, float h, float d, float grow) {
        String name = "p" + counter++;
        mesh.getRoot().addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v)
                .addBox(x, y, z, w, h, d, new CubeDeformation(grow)), PartPose.ZERO);
        specs.add(new Spec(name, slot, false));
        return this;
    }

    /** A rotated box pivoting around (px, py, pz). */
    public PartBuilder rotatedBox(int slot, float px, float py, float pz, float xRot, float yRot, float zRot,
                                  float x, float y, float z, float w, float h, float d) {
        String name = "p" + counter++;
        mesh.getRoot().addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0)
                .addBox(x, y, z, w, h, d, CubeDeformation.NONE), PartPose.offsetAndRotation(px, py, pz, xRot, yRot, zRot));
        specs.add(new Spec(name, slot, false));
        return this;
    }

    /**
     * A tapered voxel spike: stacked boxes growing along local -Y (up) from the pivot, then rotated.
     * Positive zRot leans the tip towards +X (character's left); positive xRot leans it towards the face (-Z).
     */
    public PartBuilder spike(int slot, float px, float py, float pz, float xRot, float yRot, float zRot, Segment... segments) {
        String name = "p" + counter++;
        CubeListBuilder cubes = CubeListBuilder.create().texOffs(0, 0);
        float offset = 0;
        for (Segment segment : segments) {
            float half = segment.size() / 2;
            cubes.addBox(-half, -offset - segment.length(), -half, segment.size(), segment.length(), segment.size(), CubeDeformation.NONE);
            offset += segment.length() - 0.15F;
        }
        mesh.getRoot().addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(px, py, pz, xRot, yRot, zRot));
        specs.add(new Spec(name, slot, true));
        return this;
    }

    public List<Piece> bake() {
        ModelPart root = LayerDefinition.create(mesh, textureWidth, textureHeight).bakeRoot();
        List<Piece> pieces = new ArrayList<>(specs.size());
        for (Spec spec : specs) pieces.add(new Piece(spec.name(), root.getChild(spec.name()), spec.slot(), spec.sway()));
        return List.copyOf(pieces);
    }

    /** Raw access for parts that need their own child hierarchy (the tail). */
    public PartDefinition root() { return mesh.getRoot(); }
    public ModelPart bakeRoot() { return LayerDefinition.create(mesh, textureWidth, textureHeight).bakeRoot(); }
}
