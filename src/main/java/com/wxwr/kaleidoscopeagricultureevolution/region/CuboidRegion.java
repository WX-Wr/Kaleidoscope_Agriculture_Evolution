package com.wxwr.kaleidoscopeagricultureevolution.region;

import net.minecraft.core.BlockPos;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 由两个角点定义的轴对齐矩形区域。
 *
 * <p>两个角点可以任意顺序传入；区域会对其进行规范化，
 * 使得 {@link #getMinimumPoint()} 始终返回较小的角点，
 * {@link #getMaximumPoint()} 始终返回较大的角点。
 *
 * <p>在开始操作之前，使用 {@link #checkAreaCap()} 来强制执行
 * {@value FlatRegion#MAX_AREA} 方块的农田限制。
 */
public class CuboidRegion implements FlatRegion {

    private final BlockPos pos1;
    private final BlockPos pos2;
    private final BlockPos minPoint;
    private final BlockPos maxPoint;
    private final int volume;
    private final int area;

    /**
     * 由两个角点创建矩形区域（任意顺序）。
     *
     * @throws ArithmeticException 如果体积会溢出 int
     */
    public CuboidRegion(BlockPos pos1, BlockPos pos2) {
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.minPoint = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
        this.maxPoint = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );

        int dx = maxPoint.getX() - minPoint.getX() + 1;
        int dy = maxPoint.getY() - minPoint.getY() + 1;
        int dz = maxPoint.getZ() - minPoint.getZ() + 1;

        // 使用 long 算术来防止溢出
        long vol = (long) dx * dy * dz;
        if (vol > Integer.MAX_VALUE) {
            throw new ArithmeticException(
                    "CuboidRegion 体积 " + vol + " 超过了 Integer.MAX_VALUE"
            );
        }
        this.volume = (int) vol;

        long ar = (long) dx * dz;
        if (ar > Integer.MAX_VALUE) {
            throw new ArithmeticException(
                    "CuboidRegion 面积 " + ar + " 超过了 Integer.MAX_VALUE"
            );
        }
        this.area = (int) ar;
    }

    /** 返回最初传入构造函数的第一个角点。 */
    public BlockPos getPos1() {
        return pos1;
    }

    /** 返回最初传入构造函数的第二个角点。 */
    public BlockPos getPos2() {
        return pos2;
    }

    // ---- Region / FlatRegion ------------------------------------------------

    @Override
    public boolean contains(BlockPos pos) {
        return pos.getX() >= minPoint.getX() && pos.getX() <= maxPoint.getX()
            && pos.getY() >= minPoint.getY() && pos.getY() <= maxPoint.getY()
            && pos.getZ() >= minPoint.getZ() && pos.getZ() <= maxPoint.getZ();
    }

    @Override
    public BlockPos getMinimumPoint() {
        return minPoint;
    }

    @Override
    public BlockPos getMaximumPoint() {
        return maxPoint;
    }

    @Override
    public int getVolume() {
        return volume;
    }

    @Override
    public int getMinY() {
        return minPoint.getY();
    }

    @Override
    public int getMaxY() {
        return maxPoint.getY();
    }

    @Override
    public int getArea() {
        return area;
    }

    // ---- 扩展 / 收缩 --------------------------------------------------

    /**
     * 返回一个新的 CuboidRegion，在所有六个轴方向上
     * 扩展了 {@code amount} 个方块。
     *
     * <p>传入负数则收缩区域。收缩超过中心也是允许的——
     * 生成的角点会简单地交换顺序。
     */
    public CuboidRegion expand(int amount) {
        return new CuboidRegion(
                pos1.offset(-amount, -amount, -amount),
                pos2.offset( amount,  amount,  amount)
        );
    }

    /**
     * 返回一个新的 CuboidRegion，在所有六个轴方向上
     * 收缩了 {@code amount} 个方块。等价于 {@code expand(-amount)}。
     */
    public CuboidRegion contract(int amount) {
        return expand(-amount);
    }

    // ---- 迭代器 -----------------------------------------------------------

    @Override
    public Iterator<BlockPos> iterator() {
        return new Iterator<>() {
            private int x = minPoint.getX();
            private int y = minPoint.getY();
            private int z = minPoint.getZ();

            @Override
            public boolean hasNext() {
                return y <= maxPoint.getY();
            }

            @Override
            public BlockPos next() {
                if (!hasNext()) throw new NoSuchElementException();
                BlockPos pos = new BlockPos(x, y, z);
                x++;
                if (x > maxPoint.getX()) {
                    x = minPoint.getX();
                    z++;
                    if (z > maxPoint.getZ()) {
                        z = minPoint.getZ();
                        y++;
                    }
                }
                return pos;
            }
        };
    }
}
