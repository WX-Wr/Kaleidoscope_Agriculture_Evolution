package com.wxwr.kaleidoscopeagricultureevolution.region;

/**
 * 定义鞭子上可用的五种区域编辑模式。
 *
 * <p>潜行并手持鞭子时，通过鼠标滚轮循环切换模式。
 */
public enum RegionMode {

    /** 由两个对角定义的轴对齐矩形。 */
    RECTANGLE(0, "rectangle", true),

    /** 由中心位置和半径定义的圆形/椭圆形（已禁用）。 */
    CIRCLE(4, "circle", false),

    /** 合并模式——向现有区域组中添加新矩形（已禁用）。 */
    UNION(1, "union", false),

    /** 补集模式——从现有区域中减去新矩形（已禁用）。 */
    COMPLEMENT(2, "complement", false),

    /** 删除模式——通过点击方块来选择并移除现有区域。 */
    DELETE(3, "delete", true);

    private final int id;
    private final String key;
    private final boolean scrollSelectable;

    RegionMode(int id, String key, boolean scrollSelectable) {
        this.id = id;
        this.key = key;
        this.scrollSelectable = scrollSelectable;
    }

    public int getId() {
        return id;
    }

    /** 语言键后缀，例如 "rectangle" → "message....mode_rectangle"。 */
    public String getKey() {
        return key;
    }

    /** 是否允许通过滚轮切换到该模式；未完成模式仍会显示在列表中。 */
    public boolean isScrollSelectable() {
        return scrollSelectable;
    }

    /** 从数字 id 解析模式。未知 id 默认为 RECTANGLE。 */
    public static RegionMode fromId(int id) {
        for (RegionMode m : values()) {
            if (m.id == id) return m;
        }
        return RECTANGLE;
    }

    /** 切换到下一个模式（循环）。 */
    public RegionMode next() {
        RegionMode[] values = values();
        for (int i = 1; i <= values.length; i++) {
            RegionMode candidate = values[(this.ordinal() + i) % values.length];
            if (candidate.isScrollSelectable()) return candidate;
        }
        return this;
    }

    /** 切换到上一个模式（循环）。 */
    public RegionMode prev() {
        RegionMode[] values = values();
        for (int i = 1; i <= values.length; i++) {
            RegionMode candidate = values[(this.ordinal() - i + values.length) % values.length];
            if (candidate.isScrollSelectable()) return candidate;
        }
        return this;
    }

    // ---- 选择子状态 ------------------------------------------------

    /**
     * 追踪玩家在单个模式交互流程中所处的阶段。
     */
    public enum SelectState {
        /** 未在进行编辑——显示模式列表。 */
        IDLE(0),

        /**
         * 编辑进行中——第一个点（角点/中心）已被
         * 记录，等待玩家完成第二个点。
         */
        EDITING(1),

        /**
         * 两个点均已记录——渲染区域预览，
         * 玩家可以重新进入编辑或确认完成。
         */
        PREVIEW(2);

        private final int id;

        SelectState(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static SelectState fromId(int id) {
            for (SelectState s : values()) {
                if (s.id == id) return s;
            }
            return IDLE;
        }
    }
}
