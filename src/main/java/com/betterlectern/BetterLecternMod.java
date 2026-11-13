package com.betterlectern;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

import org.jspecify.annotations.NonNull;

public class BetterLecternMod implements ModInitializer {

    @Override
    public void onInitialize() {
        registerLecternDispenserBehavior(Items.WRITTEN_BOOK);
        registerLecternDispenserBehavior(Items.WRITABLE_BOOK);
    }

    private void registerLecternDispenserBehavior(@NonNull Item bookItem) {
        DispenserBlock.registerBehavior(bookItem, new DefaultDispenseItemBehavior() {
            @Override
            protected @NonNull ItemStack execute(@NonNull BlockSource source, @NonNull ItemStack stack) {
                ServerLevel world = source.level();
                @SuppressWarnings("null")
                @NonNull Direction facing = source.state().getValue(DispenserBlock.FACING);
                BlockPos targetPos = source.pos().relative(facing);

                if (world.getBlockEntity(targetPos) instanceof LecternBlockEntity lectern) {
                    if (!world.getBlockState(targetPos).getValue(LecternBlock.HAS_BOOK)) {
                        lectern.setBook(stack.split(1));
                        return stack;
                    }
                }

                return super.execute(source, stack);
            }
        });
    }
}
