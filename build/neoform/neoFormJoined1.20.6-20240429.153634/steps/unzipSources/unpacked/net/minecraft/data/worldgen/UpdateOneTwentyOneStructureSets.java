package net.minecraft.data.worldgen;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

public interface UpdateOneTwentyOneStructureSets {
    static void bootstrap(BootstrapContext<StructureSet> p_321825_) {
        HolderGetter<Structure> holdergetter = p_321825_.lookup(Registries.STRUCTURE);
        p_321825_.register(
            BuiltinStructureSets.TRIAL_CHAMBERS,
            new StructureSet(
                holdergetter.getOrThrow(BuiltinStructures.TRIAL_CHAMBERS), new RandomSpreadStructurePlacement(34, 12, RandomSpreadType.LINEAR, 94251327)
            )
        );
    }
}
