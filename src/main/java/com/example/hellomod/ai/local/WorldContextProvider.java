package com.example.hellomod.ai.local;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;

import java.util.*;

/**
 * Компактний контекст світу для LLM (без "сирого списку блоків").
 *
 * Зафіксовані параметри скану:
 * - radius X/Z: 8 (17x17)
 * - Y: від -1 до +4 від ніг гравця
 *
 * Формат:
 * 1) коротке резюме (1–5 рядків)
 * 2) топ-об'єкти/перешкоди (обмежено, з координатами)
 */
public final class WorldContextProvider {

    public static final int RADIUS_XZ = 8; // 17x17
    public static final int Y_FROM = -1;   // relative to feet
    public static final int Y_TO = 4;      // relative to feet

    // limits for “top obstacles”
    private static final int MAX_FEATURE_LINES = 12;
    private static final int MAX_WATER_POINTS = 4;
    private static final int MAX_TREE_POINTS = 6;
    private static final int MAX_OTHER_OBS_POINTS = 6;
    private static final int CONTEXT_MAX_LINES = 30;
    private static final int CONTEXT_MAX_CHARS = 2000;

    private WorldContextProvider() {}

    /** Новий компактний формат (саме те, що просиш у 5.3) */
    public static String buildContextText(ServerLevel level, BlockPos feetOrigin) {
        if (level == null || feetOrigin == null) return "";

        final int ox = feetOrigin.getX();
        final int oy = feetOrigin.getY();
        final int oz = feetOrigin.getZ();

        // Per-cell derived info
        CellInfo[][] cells = new CellInfo[17][17];

        // surface stats
        Map<String, Integer> surfaceBlockCounts = new HashMap<>();
        int minSurfaceY = Integer.MAX_VALUE;
        int maxSurfaceY = Integer.MIN_VALUE;

        int emptyCols = 0;
        int obstacleCols = 0;
        int waterCols = 0;

        // Gather candidate features
        List<Feature> water = new ArrayList<>();
        List<Feature> trees = new ArrayList<>();
        List<Feature> otherObs = new ArrayList<>();

        for (int dz = -RADIUS_XZ; dz <= RADIUS_XZ; dz++) {
            for (int dx = -RADIUS_XZ; dx <= RADIUS_XZ; dx++) {
                int ix = dx + RADIUS_XZ;
                int iz = dz + RADIUS_XZ;

                CellInfo ci = scanColumn(level, ox, oy, oz, dx, dz);
                cells[iz][ix] = ci;

                if (ci.surfaceYRel < minSurfaceY) minSurfaceY = ci.surfaceYRel;
                if (ci.surfaceYRel > maxSurfaceY) maxSurfaceY = ci.surfaceYRel;

                surfaceBlockCounts.merge(ci.surfaceBlockId, 1, Integer::sum);

                if (ci.hasWater) {
                    waterCols++;
                    water.add(new Feature("water", dx, dz, ci.nearestWaterYRel, ci.nearestWaterBlockId, distChebyshev(dx, dz)));
                } else if (ci.hasObstacle) {
                    obstacleCols++;
                    // classify
                    if (ci.isTreeLike) {
                        trees.add(new Feature("tree", dx, dz, ci.firstObstacleYRel, ci.firstObstacleBlockId, distChebyshev(dx, dz)));
                    } else {
                        otherObs.add(new Feature("obstacle", dx, dz, ci.firstObstacleYRel, ci.firstObstacleBlockId, distChebyshev(dx, dz)));
                    }
                } else {
                    emptyCols++;
                }
            }
        }

        String surfaceTop = topKey(surfaceBlockCounts, "minecraft:grass_block");
        int slopeDelta = (maxSurfaceY == Integer.MIN_VALUE || minSurfaceY == Integer.MAX_VALUE) ? 0 : (maxSurfaceY - minSurfaceY);
        String slope = slopeText(slopeDelta);

        // nearest water direction + dist
        Feature nearestWater = pickNearest(water);
        Feature nearestTree = pickNearest(trees);

        boolean freeSpaceYes = emptyCols >= 200; // ~69% of 289
        String freeSpace = freeSpaceYes ? "yes" : "no";

        StringBuilder sb = new StringBuilder(900);

        // 1) Summary (1–5 lines)
        sb.append("WORLD_CONTEXT v2\n");
        sb.append("Surface: ").append(surfaceTop).append(", slope: ").append(slope).append(" (deltaY=").append(slopeDelta).append(")\n");

        if (nearestWater != null) {
            sb.append("Nearby: water (").append(dir(nearestWater.dx, nearestWater.dz))
                    .append(" ~").append(nearestWater.dist).append(")\n");
        } else {
            sb.append("Nearby: water (none in 17x17)\n");
        }

        if (nearestTree != null) {
            sb.append("Nearby: trees (").append(dir(nearestTree.dx, nearestTree.dz))
                    .append(" ~").append(nearestTree.dist).append(")\n");
        } else {
            sb.append("Nearby: trees (none)\n");
        }

        sb.append("Free space (y=0..+4): ").append(freeSpace)
                .append(" (empty=").append(emptyCols)
                .append(", obstacle=").append(obstacleCols)
                .append(", water=").append(waterCols).append(")\n");

        // 2) Top obstacles/objects (limited)
        sb.append("Top objects:\n");

        int lines = 0;
        lines += appendTop(sb, "water edge", water, MAX_WATER_POINTS, lines);
        lines += appendTop(sb, "tree", trees, MAX_TREE_POINTS, lines);
        lines += appendTop(sb, "obstacle", otherObs, MAX_OTHER_OBS_POINTS, lines);

        if (lines == 0) {
            sb.append("- none\n");
        }

        String raw = sb.toString();
        String budgeted = applyContextBudget(raw);

        return budgeted;

    }

    private static int appendTop(StringBuilder sb, String label, List<Feature> list, int max, int already) {
        if (already >= MAX_FEATURE_LINES) return 0;
        if (list == null || list.isEmpty()) return 0;

        list.sort(Comparator
                .comparingInt((Feature f) -> f.dist)
                .thenComparingInt(f -> Math.abs(f.dx) + Math.abs(f.dz)));

        int count = 0;
        for (Feature f : list) {
            if (already + count >= MAX_FEATURE_LINES) break;
            if (count >= max) break;

            sb.append("- ").append(label)
                    .append(" at dx=").append(f.dx).append(" dz=").append(f.dz)
                    .append(" y=").append(f.yRel)
                    .append(" block=").append(f.blockId)
                    .append("\n");
            count++;
        }
        return count;
    }

    private static Feature pickNearest(List<Feature> list) {
        if (list == null || list.isEmpty()) return null;
        return list.stream()
                .min(Comparator
                        .comparingInt((Feature f) -> f.dist)
                        .thenComparingInt(f -> Math.abs(f.dx) + Math.abs(f.dz)))
                .orElse(null);
    }

    private static String topKey(Map<String, Integer> counts, String def) {
        if (counts == null || counts.isEmpty()) return def;
        return counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(def);
    }

    private static String slopeText(int deltaY) {
        if (deltaY <= 1) return "flat/mild";
        if (deltaY <= 2) return "medium";
        return "steep";
    }

    private static String dir(int dx, int dz) {
        // crude 8-way
        String ns = dz < 0 ? "north" : (dz > 0 ? "south" : "");
        String ew = dx < 0 ? "west" : (dx > 0 ? "east" : "");
        if (ns.isEmpty() && ew.isEmpty()) return "here";
        if (!ns.isEmpty() && !ew.isEmpty()) return ns + "-" + ew;
        return ns.isEmpty() ? ew : ns;
    }

    private static int distChebyshev(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz));
    }

    private static CellInfo scanColumn(ServerLevel level, int ox, int oy, int oz, int dx, int dz) {
        // Find surface within y range: highest non-air in [oy-1..oy+4]
        int surfaceYRel = Integer.MIN_VALUE;
        String surfaceBlockId = "unknown";

        boolean hasWater = false;
        String nearestWaterBlockId = "unknown";
        int nearestWaterYRel = 0;

        boolean hasObstacle = false;
        String firstObstacleBlockId = "minecraft:air";
        int firstObstacleYRel = 0;
        boolean treeLike = false;

        // scan from top to bottom to find surface
        for (int ry = Y_TO; ry >= Y_FROM; ry--) {
            BlockPos p = new BlockPos(ox + dx, oy + ry, oz + dz);
            BlockState s = level.getBlockState(p);
            if (!s.isAir()) {
                surfaceYRel = ry;
                surfaceBlockId = blockId(s);
                break;
            }
        }
        if (surfaceYRel == Integer.MIN_VALUE) {
            surfaceYRel = Y_FROM; // fallback
            surfaceBlockId = "minecraft:air";
        }

        // scan build volume y=0..+4 for obstacle/water details
        for (int ry = 0; ry <= Y_TO; ry++) {
            BlockPos p = new BlockPos(ox + dx, oy + ry, oz + dz);
            BlockState s = level.getBlockState(p);

            if (isWater(s)) {
                if (!hasWater) {
                    hasWater = true;
                    nearestWaterYRel = ry;
                    nearestWaterBlockId = blockId(s);
                }
            }

            if (!s.isAir()) {
                if (!hasObstacle) {
                    hasObstacle = true;
                    firstObstacleYRel = ry;
                    firstObstacleBlockId = blockId(s);
                    treeLike = isTreeLikeId(firstObstacleBlockId);
                } else if (!treeLike) {
                    // якщо перший не дерево, але далі є дерево — теж позначимо
                    if (isTreeLikeId(blockId(s))) treeLike = true;
                }
            }
        }

        CellInfo ci = new CellInfo();
        ci.surfaceYRel = surfaceYRel;
        ci.surfaceBlockId = surfaceBlockId;

        ci.hasWater = hasWater;
        ci.nearestWaterYRel = nearestWaterYRel;
        ci.nearestWaterBlockId = nearestWaterBlockId;

        ci.hasObstacle = hasObstacle;
        ci.firstObstacleYRel = firstObstacleYRel;
        ci.firstObstacleBlockId = firstObstacleBlockId;
        ci.isTreeLike = treeLike;

        return ci;
    }

    private static boolean isWater(BlockState s) {
        try {
            return s.getMaterial() == Material.WATER;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTreeLikeId(String id) {
        if (id == null) return false;
        // простий хак: підходить для oak/spruce/birch/jungle/acacia/dark_oak + leaves
        return id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_leaves");
    }

    private static String blockId(BlockState s) {
        try {
            Block b = s.getBlock();
            return String.valueOf(Registry.BLOCK.getKey(b));
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static final class CellInfo {
        int surfaceYRel;
        String surfaceBlockId;

        boolean hasWater;
        int nearestWaterYRel;
        String nearestWaterBlockId;

        boolean hasObstacle;
        int firstObstacleYRel;
        String firstObstacleBlockId;

        boolean isTreeLike;
    }

    private static final class Feature {
        final String kind;
        final int dx, dz;
        final int yRel;
        final String blockId;
        final int dist;

        Feature(String kind, int dx, int dz, int yRel, String blockId, int dist) {
            this.kind = kind;
            this.dx = dx;
            this.dz = dz;
            this.yRel = yRel;
            this.blockId = blockId;
            this.dist = dist;
        }
    }

    private static String applyContextBudget(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        // Спочатку обрізаємо по рядках
        String[] lines = raw.split("\\R");
        if (lines.length > CONTEXT_MAX_LINES) {
            StringBuilder sb = new StringBuilder(Math.min(raw.length(), CONTEXT_MAX_CHARS + 64));
            for (int i = 0; i < CONTEXT_MAX_LINES; i++) {
                sb.append(lines[i]).append("\n");
            }
            sb.append("...(context truncated: lines)\n");
            raw = sb.toString();
        }

        // Потім по символах
        if (raw.length() > CONTEXT_MAX_CHARS) {
            raw = raw.substring(0, CONTEXT_MAX_CHARS) + "\n...(context truncated: chars)\n";
        }

        return raw;
    }
}
