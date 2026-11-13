package com.betterlectern.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NonNull;

public interface SingleStackInventory {
    int getContainerSize();

    @NonNull ItemStack getStack();

    void setStack(@NonNull ItemStack stack);

    boolean isValid(int slot, @NonNull ItemStack stack);

    boolean canPlayerUse(@NonNull Player player);
}
