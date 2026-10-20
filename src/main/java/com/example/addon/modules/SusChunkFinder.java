package com.example.addon.modules;
 
import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
 
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
 
/**
 * Highlights chunks that look player-made, based on the chunk data the server
 * sends you. A chunk is "sus" when it contains several different man-made block
 * types, or several block entities (chests, furnaces, barrels...).
 *
 * Client-side only. It reads chunks the server already sent; it does not touch packets.
 */
public class SusChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
 
    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Man-made blocks that count towards a chunk being sus.")
        .defaultValue(
            Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST,
            Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.CRAFTING_TABLE,
            Blocks.HOPPER, Blocks.TORCH, Blocks.WALL_TORCH, Blocks.LANTERN,
            Blocks.OBSIDIAN, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.BEACON,
            Blocks.TNT, Blocks.BOOKSHELF, Blocks.OAK_PLANKS, Blocks.COBBLESTONE_STAIRS
        )
        .build()
    );
 
    private final Setting<Integer> minTypes = sgGeneral.add(new IntSetting.Builder()
        .name("min-block-types")
        .description("Different listed block types needed in one chunk.")
        .defaultValue(3).min(1).sliderMax(15)
        .build()
    );
 
    private final Setting<Integer> minBlockEntities = sgGeneral.add(new IntSetting.Builder()
        .name("min-block-entities")
        .description("Chests, furnaces, signs etc. in one chunk that also flag it. 0 disables.")
        .defaultValue(4).min(0).sliderMax(50)
        .build()
    );
 
    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Print coordinates in chat when a new sus chunk is found.")
        .defaultValue(true)
        .build()
    );
 
    private final Setting<Integer> yMin = sgRender.add(new IntSetting.Builder()
        .name("y-min").defaultValue(-64).sliderRange(-64, 320).build());
 
    private final Setting<Integer> yMax = sgRender.add(new IntSetting.Builder()
        .name("y-max").defaultValue(320).sliderRange(-64, 320).build());
 
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());
 
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(255, 60, 60, 25)).build());
 
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(255, 60, 60, 220)).build());
 
    private final Map<ChunkPos, Integer> sus = new HashMap<>();
    private final Set<ChunkPos> seen = new HashSet<>();
 
    public SusChunkFinder() {
        super(AddonTemplate.CATEGORY, "sus-chunk-finder", "Highlights chunks that look player-built.");
    }
 
    @Override
    public void onActivate() {
        sus.clear();
        seen.clear();
    }
 
    @EventHandler
    private void onLeave(GameLeftEvent event) {
        sus.clear();
        seen.clear();
    }
 
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        Chunk chunk = event.chunk();
        ChunkPos pos = chunk.getPos();
 
        int types = countTypes(chunk);
        int entities = chunk.getBlockEntityPositions().size();
 
        boolean flagged = types >= minTypes.get()
            || (minBlockEntities.get() > 0 && entities >= minBlockEntities.get());
 
        if (!flagged) {
            sus.remove(pos);
            return;
        }
 
        sus.put(pos, types);
        if (notify.get() && seen.add(pos)) {
            info("Sus chunk at (highlight)%d, %d(default) - %d block types, %d block entities.",
                pos.getStartX() + 8, pos.getStartZ() + 8, types, entities);
        }
    }
 
    /** Counts how many distinct listed block types occur in the chunk. Palette-based, so it is cheap. */
    private int countTypes(Chunk chunk) {
        Set<Block> found = new HashSet<>();
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            for (Block block : blocks.get()) {
                if (found.contains(block)) continue;
                if (section.hasAny(state -> state.isOf(block))) found.add(block);
            }
        }
        return found.size();
    }
 
    @EventHandler
    private void onRender(Render3DEvent event) {
        for (ChunkPos pos : sus.keySet()) {
            double x = pos.getStartX();
            double z = pos.getStartZ();
            event.renderer.box(x, yMin.get(), z, x + 16, yMax.get(), z + 16,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }
 
    @Override
    public String getInfoString() {
        return String.valueOf(sus.size());
    }
}
