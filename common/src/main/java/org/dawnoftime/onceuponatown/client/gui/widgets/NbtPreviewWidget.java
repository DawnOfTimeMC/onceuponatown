package org.dawnoftime.onceuponatown.client.gui.widgets;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a building .nbt structure as an interactive 3D isometric preview inside a GUI zone.
 * Drag to rotate, scroll to zoom.
 */
public class NbtPreviewWidget {

    private record BlockData(BlockPos pos, BlockState state) {}

    private static final Set<Block> EXCLUDED = Set.of(
        Blocks.AIR,
        Blocks.CAVE_AIR,
        Blocks.VOID_AIR,
        Blocks.STRUCTURE_VOID,
        Blocks.JIGSAW,
        Blocks.STRUCTURE_BLOCK
    );

    private static final Map<String, List<BlockData>> CACHE = new HashMap<>();

    private final int x, y, width, height;

    private List<BlockData> blocks = null;
    private List<BlockData> sortedBlocks = null;
    private List<BlockData> lastSortedSource = null;
    private float lastSortedRotationY = Float.NaN;
    private boolean loadFailed = false;

    private float rotationY = 45f;
    private float scale = 4f;
    private boolean locked = false;

    private boolean dragging = false;
    private double lastDragX;

    public NbtPreviewWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setLocked(boolean locked) { this.locked = locked; }
    public void setScale(float scale) { this.scale = scale; }

    /**
     * Sets the structure to display.
     * @param nbtPath the "nbt" field from BuildingDef, e.g. "onceuponatown:plains/jobs/wild_stone"
     */
    public void setStructure(String nbtPath) {
        if (CACHE.containsKey(nbtPath)) {
            blocks = CACHE.get(nbtPath);
            loadFailed = false;
            return;
        }
        blocks = null;
        loadFailed = false;
        try {
            ResourceLocation defLoc = new ResourceLocation(nbtPath);
            String classLoaderPath = "/data/" + defLoc.getNamespace()
                + "/structures/" + defLoc.getPath() + ".nbt";
            InputStream stream = NbtPreviewWidget.class.getResourceAsStream(classLoaderPath);
            if (stream == null) throw new java.io.IOException("NBT not found: " + classLoaderPath);
            CompoundTag nbt = NbtIo.readCompressed(stream);
            stream.close();

            ListTag paletteTag = nbt.getList("palette", Tag.TAG_COMPOUND);
            BlockState[] palette = new BlockState[paletteTag.size()];
            for (int i = 0; i < paletteTag.size(); i++) {
                try {
                    palette[i] = NbtUtils.readBlockState(
                        BuiltInRegistries.BLOCK.asLookup(),
                        paletteTag.getCompound(i)
                    );
                } catch (Exception ex) {
                    palette[i] = Blocks.AIR.defaultBlockState();
                }
            }

            List<BlockData> parsed = new ArrayList<>();
            ListTag blocksTag = nbt.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocksTag.size(); i++) {
                CompoundTag bt = blocksTag.getCompound(i);
                ListTag posTag = bt.getList("pos", Tag.TAG_INT);
                int bx = posTag.getInt(0);
                int by = posTag.getInt(1);
                int bz = posTag.getInt(2);
                int stateIdx = bt.getInt("state");
                if (stateIdx >= 0 && stateIdx < palette.length) {
                    BlockState state = palette[stateIdx];
                    if (!EXCLUDED.contains(state.getBlock())) {
                        parsed.add(new BlockData(new BlockPos(bx, by, bz), state));
                    }
                }
            }
            blocks = parsed;
            sortedBlocks = null;
            CACHE.put(nbtPath, parsed);
        } catch (Exception e) {
            loadFailed = true;
            blocks = List.of();
        }
    }

    // Painter's algorithm sort: back-to-front by view-projected depth.
    // depthKey = dot(blockPos, viewDir), ascending = furthest first.
    // viewDir in XZ is derived from rotationY; Y factor accounts for the 30-degree XP tilt.
    // Tracks both the source list and rotation so switching buildings invalidates the cache automatically.
    private List<BlockData> getSortedBlocks() {
        if (sortedBlocks != null && lastSortedSource == blocks && lastSortedRotationY == rotationY) return sortedBlocks;
        double rad = Math.toRadians(rotationY);
        double sinR = Math.sin(rad);
        double cosR = Math.cos(rad);
        sortedBlocks = new ArrayList<>(blocks);
        sortedBlocks.sort(Comparator.comparingDouble(b ->
            b.pos().getX() * sinR + b.pos().getZ() * cosR - b.pos().getY() * 0.5
        ));
        lastSortedSource = blocks;
        lastSortedRotationY = rotationY;
        return sortedBlocks;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (loadFailed) {
            String msg = "Load error";
            var font = Minecraft.getInstance().font;
            g.drawString(font, msg, x + (width - font.width(msg)) / 2, y + height / 2 - 4, 0xFFCC4444, false);
            return;
        }

        if (blocks == null || blocks.isEmpty()) {
            String msg = blocks == null ? "..." : "No blocks";
            var font = Minecraft.getInstance().font;
            g.drawString(font, msg, x + (width - font.width(msg)) / 2, y + height / 2 - 4, 0xFF888888, false);
            return;
        }

        int maxX = 0, maxY = 0, maxZ = 0;
        for (BlockData b : blocks) {
            if (b.pos().getX() > maxX) maxX = b.pos().getX();
            if (b.pos().getY() > maxY) maxY = b.pos().getY();
            if (b.pos().getZ() > maxZ) maxZ = b.pos().getZ();
        }

        g.enableScissor(x - 1, y + 9, x + width - 6, y + height - 2);
        // Depth test is disabled: painter sort owns between-block ordering.
        // scale(-Y) makes view-space Z unreliable for depth comparisons anyway.
        // Backface culling handles within-block face visibility.
        RenderSystem.disableDepthTest();

        var pose = g.pose();
        pose.pushPose();

        pose.translate(x - 1 + (width - 5) / 2.0, y + 9 + (height - 11) / 2.0, 100.0);

        // scale(s,-s,s): compensates for GUI screen-space Y-down projection.
        // The GUI projection flips all triangle winding CCW->CW. The -Y scale
        // re-flips it, restoring the correct winding so block face culling works.
        pose.scale(scale, -scale, scale);
        // Counter-flip Y in the normal matrix only: the -Y scale makes UP normals point DOWN,
        // causing the shader to apply the 0.5 dark factor to top faces. Position winding is untouched.
        Matrix3f nm = pose.last().normal();
        nm.m10(-nm.m10());
        nm.m11(-nm.m11());
        nm.m12(-nm.m12());
        pose.mulPose(Axis.XP.rotationDegrees(30f));
        // Freeze the normal matrix here (after tilt, before user rotation).
        // The YP rotation updates vertex positions correctly but must not alter normals,
        // otherwise the shader receives rotation-dependent normals and shading shifts subtly.
        Matrix3f frozenNormals = new Matrix3f(pose.last().normal());
        pose.mulPose(Axis.YP.rotationDegrees(rotationY));
        pose.last().normal().set(frozenNormals);

        pose.translate(-(maxX + 1) / 2.0, -(maxY + 1) / 2.0, -(maxZ + 1) / 2.0);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        BlockRenderDispatcher brd = Minecraft.getInstance().getBlockRenderer();

        // Flush per block in painter sort order so all render types (solid, cutout, etc.)
        // are submitted to the GPU atomically back-to-front.
        BlockState blackConcrete = Blocks.BLACK_CONCRETE.defaultBlockState();
        for (BlockData b : getSortedBlocks()) {
            pose.pushPose();
            pose.translate(b.pos().getX(), b.pos().getY(), b.pos().getZ());
            brd.renderSingleBlock(
                locked ? blackConcrete : b.state(),
                pose,
                buffers,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
            );
            pose.popPose();
            buffers.endBatch(RenderType.solid());
            buffers.endBatch(RenderType.cutout());
            buffers.endBatch(RenderType.cutoutMipped());
            buffers.endBatch(RenderType.translucent());
        }

        buffers.endBatch();
        pose.popPose();

        g.disableScissor();

        if (locked) {
            // Center within the scissor-clipped visible zone (matches g.enableScissor offsets above)
            int vx = x - 1;
            int vy = y + 9;
            int vw = width - 5;
            int vh = height - 11;
            int px = vx + (vw - 8) / 2;
            int py = vy + (vh - 10) / 2;
            RenderSystem.disableDepthTest();
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            drawPadlockIcon(g, px, py);
            g.pose().popPose();
            RenderSystem.enableDepthTest();
        }
    }

    public static void drawPadlockIcon(GuiGraphics g, int bx, int by) {
        int c = 0xFFCCCCCC;
        int k = 0xFF111111;
        g.fill(bx + 2, by,     bx + 6, by + 1, c);
        g.fill(bx + 1, by + 1, bx + 2, by + 4, c);
        g.fill(bx + 6, by + 1, bx + 7, by + 4, c);
        g.fill(bx,     by + 4, bx + 8, by + 10, c);
        g.fill(bx + 3, by + 5, bx + 5, by + 7, k);
        g.fill(bx + 3, by + 7, bx + 5, by + 9, k);
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!isOver(mx, my)) return false;
        scale = (float) Math.max(2.0, Math.min(24.0, scale + delta * 0.5));
        return true;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && isOver(mx, my)) {
            dragging = true;
            lastDragX = mx;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            rotationY += (float)(mx - lastDragX) * 0.8f;
            lastDragX = mx;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    private boolean isOver(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
