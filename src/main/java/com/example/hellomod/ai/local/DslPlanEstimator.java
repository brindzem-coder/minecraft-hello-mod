package com.example.hellomod.ai.local;

import java.util.List;

/**
 * Груба (але корисна) оцінка "плану змін" для DSL:
 * - estimated changed blocks (worst-case)
 * - union bounding box (relative to player position)
 * - max distance from player (Euclidean, по кутах bbox команд)
 *
 * DSL, який оцінюємо:
 *  - PLACE_BLOCK block dx dy dz
 *  - BUILD_BOX w h d   (у виконанні ставиться з base = player + (2,0,2))
 *  - CLEAR_AROUND r    (чистить x/z [-r..r], y [0..10] від player)
 */
public final class DslPlanEstimator {

    private DslPlanEstimator() {}

    public static PlanEstimate estimate(List<String> lines) {
        PlanEstimate e = new PlanEstimate();
        if (lines == null) return e;

        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            if (parts.length == 0) continue;

            String cmd = parts[0].toUpperCase();

            switch (cmd) {
                case "PLACE_BLOCK" -> {
                    // PLACE_BLOCK block dx dy dz
                    if (parts.length < 5) {
                        e.failReason = "PLACE_BLOCK invalid format";
                        return e;
                    }
                    int dx = parseIntSafe(parts[2], Integer.MIN_VALUE);
                    int dy = parseIntSafe(parts[3], Integer.MIN_VALUE);
                    int dz = parseIntSafe(parts[4], Integer.MIN_VALUE);
                    if (dx == Integer.MIN_VALUE || dy == Integer.MIN_VALUE || dz == Integer.MIN_VALUE) {
                        e.failReason = "PLACE_BLOCK invalid coords";
                        return e;
                    }

                    e.changedBlocks += 1;
                    e.expandBbox(dx, dy, dz, dx, dy, dz);
                    e.updateMaxDistByBox(dx, dy, dz, dx, dy, dz);
                }

                case "BUILD_BOX" -> {
                    // BUILD_BOX w h d, base = (2,0,2)
                    if (parts.length < 4) {
                        e.failReason = "BUILD_BOX invalid format";
                        return e;
                    }
                    int w = parseIntSafe(parts[1], Integer.MIN_VALUE);
                    int h = parseIntSafe(parts[2], Integer.MIN_VALUE);
                    int d = parseIntSafe(parts[3], Integer.MIN_VALUE);
                    if (w <= 0 || h <= 0 || d <= 0) {
                        e.failReason = "BUILD_BOX invalid sizes";
                        return e;
                    }

                    long vol = (long) w * h * d;
                    e.changedBlocks += vol;

                    int minX = 2;
                    int minY = 0;
                    int minZ = 2;
                    int maxX = 2 + (w - 1);
                    int maxY = 0 + (h - 1);
                    int maxZ = 2 + (d - 1);

                    e.expandBbox(minX, minY, minZ, maxX, maxY, maxZ);
                    e.updateMaxDistByBox(minX, minY, minZ, maxX, maxY, maxZ);
                }

                case "CLEAR_AROUND" -> {
                    // CLEAR_AROUND r, affects x/z [-r..r], y [0..10]
                    if (parts.length < 2) {
                        e.failReason = "CLEAR_AROUND invalid format";
                        return e;
                    }
                    int r = parseIntSafe(parts[1], Integer.MIN_VALUE);
                    if (r <= 0) {
                        e.failReason = "CLEAR_AROUND invalid radius";
                        return e;
                    }

                    // Worst-case changed blocks: (2r+1)^2 * 11 (y=0..10 inclusive)
                    long area = (long) (2 * r + 1) * (2L * r + 1);
                    e.changedBlocks += area * 11L;

                    int minX = -r;
                    int minY = 0;
                    int minZ = -r;
                    int maxX = r;
                    int maxY = 10;
                    int maxZ = r;

                    e.expandBbox(minX, minY, minZ, maxX, maxY, maxZ);
                    e.updateMaxDistByBox(minX, minY, minZ, maxX, maxY, maxZ);
                }

                default -> {
                    // Якщо сюди потрапило — або нова команда, або шум.
                    // Валідатор зазвичай це відріже, але для strict-оцінки краще стопнути.
                    e.failReason = "Unknown command in estimator: " + cmd;
                    return e;
                }
            }
        }

        e.finalizeVolume();
        return e;
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return def;
        }
    }

    public static final class PlanEstimate {
        public long changedBlocks = 0;

        // union bbox relative to player
        public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        public long bboxVolume = 0;

        // Euclidean squared distance to farthest affected corner
        public long maxDistSq = 0;

        // if non-null -> estimation failed
        public String failReason = null;

        boolean hasBbox() {
            return minX != Integer.MAX_VALUE;
        }

        void expandBbox(int x1, int y1, int z1, int x2, int y2, int z2) {
            int aMinX = Math.min(x1, x2);
            int aMinY = Math.min(y1, y2);
            int aMinZ = Math.min(z1, z2);
            int aMaxX = Math.max(x1, x2);
            int aMaxY = Math.max(y1, y2);
            int aMaxZ = Math.max(z1, z2);

            if (!hasBbox()) {
                minX = aMinX; minY = aMinY; minZ = aMinZ;
                maxX = aMaxX; maxY = aMaxY; maxZ = aMaxZ;
                return;
            }

            minX = Math.min(minX, aMinX);
            minY = Math.min(minY, aMinY);
            minZ = Math.min(minZ, aMinZ);
            maxX = Math.max(maxX, aMaxX);
            maxY = Math.max(maxY, aMaxY);
            maxZ = Math.max(maxZ, aMaxZ);
        }

        void updateMaxDistByBox(int x1, int y1, int z1, int x2, int y2, int z2) {
            int aMinX = Math.min(x1, x2);
            int aMinY = Math.min(y1, y2);
            int aMinZ = Math.min(z1, z2);
            int aMaxX = Math.max(x1, x2);
            int aMaxY = Math.max(y1, y2);
            int aMaxZ = Math.max(z1, z2);

            // 8 corners
            updateCorner(aMinX, aMinY, aMinZ);
            updateCorner(aMinX, aMinY, aMaxZ);
            updateCorner(aMinX, aMaxY, aMinZ);
            updateCorner(aMinX, aMaxY, aMaxZ);
            updateCorner(aMaxX, aMinY, aMinZ);
            updateCorner(aMaxX, aMinY, aMaxZ);
            updateCorner(aMaxX, aMaxY, aMinZ);
            updateCorner(aMaxX, aMaxY, aMaxZ);
        }

        void updateCorner(int x, int y, int z) {
            long dsq = (long) x * x + (long) y * y + (long) z * z;
            if (dsq > maxDistSq) maxDistSq = dsq;
        }

        void finalizeVolume() {
            if (!hasBbox()) {
                bboxVolume = 0;
                return;
            }
            long dx = (long) (maxX - minX + 1);
            long dy = (long) (maxY - minY + 1);
            long dz = (long) (maxZ - minZ + 1);
            bboxVolume = dx * dy * dz;
        }
    }
}
