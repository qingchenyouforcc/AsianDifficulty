package net.minecraft.world.entity;

import net.minecraft.util.StringRepresentable;

public enum EquipmentSlot implements StringRepresentable {
    MAINHAND(EquipmentSlot.Type.HAND, 0, 0, "mainhand"),
    OFFHAND(EquipmentSlot.Type.HAND, 1, 5, "offhand"),
    FEET(EquipmentSlot.Type.ARMOR, 0, 1, "feet"),
    LEGS(EquipmentSlot.Type.ARMOR, 1, 2, "legs"),
    CHEST(EquipmentSlot.Type.ARMOR, 2, 3, "chest"),
    HEAD(EquipmentSlot.Type.ARMOR, 3, 4, "head"),
    BODY(EquipmentSlot.Type.BODY, 0, 6, "body");

    public static final StringRepresentable.EnumCodec<EquipmentSlot> CODEC = StringRepresentable.fromEnum(EquipmentSlot::values);
    private final EquipmentSlot.Type type;
    private final int index;
    private final int filterFlag;
    private final String name;

    private EquipmentSlot(EquipmentSlot.Type p_20739_, int p_20740_, int p_20741_, String p_20742_) {
        this.type = p_20739_;
        this.index = p_20740_;
        this.filterFlag = p_20741_;
        this.name = p_20742_;
    }

    public EquipmentSlot.Type getType() {
        return this.type;
    }

    public int getIndex() {
        return this.index;
    }

    public int getIndex(int p_147069_) {
        return p_147069_ + this.index;
    }

    public int getFilterFlag() {
        return this.filterFlag;
    }

    public String getName() {
        return this.name;
    }

    public boolean isArmor() {
        return this.type == EquipmentSlot.Type.ARMOR;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public static EquipmentSlot byName(String p_20748_) {
        EquipmentSlot equipmentslot = CODEC.byName(p_20748_);
        if (equipmentslot != null) {
            return equipmentslot;
        } else {
            throw new IllegalArgumentException("Invalid slot '" + p_20748_ + "'");
        }
    }

    public static EquipmentSlot byTypeAndIndex(EquipmentSlot.Type p_20745_, int p_20746_) {
        for (EquipmentSlot equipmentslot : values()) {
            if (equipmentslot.getType() == p_20745_ && equipmentslot.getIndex() == p_20746_) {
                return equipmentslot;
            }
        }

        throw new IllegalArgumentException("Invalid slot '" + p_20745_ + "': " + p_20746_);
    }

    public static enum Type {
        HAND,
        ARMOR,
        BODY;
    }
}
