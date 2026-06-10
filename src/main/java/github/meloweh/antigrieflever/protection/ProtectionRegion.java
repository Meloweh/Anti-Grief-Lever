package github.meloweh.antigrieflever.protection;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public record ProtectionRegion(
    Type type,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ,
    int radius
) {
    public enum Type {
        CUBOID,
        SPHERE
    }

    public boolean contains(BlockPos pos, BlockPos center) {
        if (type == Type.CUBOID) {
            return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        long dx = (long) pos.getX() - center.getX();
        long dy = (long) pos.getY() - center.getY();
        long dz = (long) pos.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz <= (long) radius * radius;
    }

    public boolean withinLimit(BlockPos center, int maxRadius) {
        if (type == Type.SPHERE) {
            return radius <= maxRadius;
        }
        return axisDistance(center.getX(), minX, maxX) <= maxRadius
            && axisDistance(center.getY(), minY, maxY) <= maxRadius
            && axisDistance(center.getZ(), minZ, maxZ) <= maxRadius;
    }

    private static long axisDistance(int center, int min, int max) {
        return Math.max(Math.abs((long) center - min), Math.abs((long) center - max));
    }

    public static ParseResult parse(String definition, BlockPos center, int maxRadius) {
        if (definition == null) {
            return ParseResult.error("Region is required");
        }

        String value = definition.trim();
        if (value.length() < 3 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            return ParseResult.error("Use [radius], [width,height], or [x1,y1,z1,x2,y2,z2]");
        }

        String body = value.substring(1, value.length() - 1).trim();
        if (body.isEmpty()) {
            return ParseResult.error("Region is required");
        }

        String[] parts = body.split(",");
        List<Integer> numbers = new ArrayList<>(parts.length);
        try {
            for (String part : parts) {
                numbers.add(Integer.parseInt(part.trim()));
            }
        } catch (NumberFormatException exception) {
            return ParseResult.error("All region values must be whole numbers");
        }

        ProtectionRegion region;
        if (numbers.size() == 1) {
            int radius = numbers.getFirst();
            if (radius < 1) {
                return ParseResult.error("Radius must be at least 1");
            }
            region = new ProtectionRegion(
                Type.SPHERE,
                center.getX() - radius,
                center.getY() - radius,
                center.getZ() - radius,
                center.getX() + radius,
                center.getY() + radius,
                center.getZ() + radius,
                radius
            );
        } else if (numbers.size() == 2) {
            int width = numbers.get(0);
            int height = numbers.get(1);
            if (width < 1 || height < 1) {
                return ParseResult.error("Width and height must be at least 1");
            }
            int horizontalLow = (width - 1) / 2;
            int horizontalHigh = width / 2;
            int verticalLow = (height - 1) / 2;
            int verticalHigh = height / 2;
            region = new ProtectionRegion(
                Type.CUBOID,
                center.getX() - horizontalLow,
                center.getY() - verticalLow,
                center.getZ() - horizontalLow,
                center.getX() + horizontalHigh,
                center.getY() + verticalHigh,
                center.getZ() + horizontalHigh,
                0
            );
        } else if (numbers.size() == 6) {
            region = new ProtectionRegion(
                Type.CUBOID,
                Math.min(numbers.get(0), numbers.get(3)),
                Math.min(numbers.get(1), numbers.get(4)),
                Math.min(numbers.get(2), numbers.get(5)),
                Math.max(numbers.get(0), numbers.get(3)),
                Math.max(numbers.get(1), numbers.get(4)),
                Math.max(numbers.get(2), numbers.get(5)),
                0
            );
        } else {
            return ParseResult.error("Use 1, 2, or 6 values");
        }

        if (!region.withinLimit(center, maxRadius)) {
            return ParseResult.error("Region exceeds the maximum radius of " + maxRadius);
        }

        String canonical = numbers.stream()
            .map(String::valueOf)
            .reduce((left, right) -> left + "," + right)
            .map(joined -> "[" + joined + "]")
            .orElseThrow();
        return ParseResult.success(region, canonical);
    }

    public record ParseResult(ProtectionRegion region, String canonicalDefinition, String error) {
        public static ParseResult success(ProtectionRegion region, String canonicalDefinition) {
            return new ParseResult(region, canonicalDefinition, null);
        }

        public static ParseResult error(String error) {
            return new ParseResult(null, null, error);
        }

        public boolean valid() {
            return region != null;
        }
    }
}
