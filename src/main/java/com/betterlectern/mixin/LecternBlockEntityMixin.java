package com.betterlectern.mixin;

import com.betterlectern.inventory.SingleStackInventory;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LecternBlockEntity.class)
public abstract class LecternBlockEntityMixin implements SingleStackInventory, Container {

    @Unique
    private static final String BOOK_PAGE_HISTORY_KEY = "BetterLecternsBookPages";

    @Unique
    private static final String BOOK_ID_KEY = "BetterLecternsBookId";

    @Unique
    private static final String UUID_KEY_PREFIX = "uuid:";

    @Unique
    private static final String HASH_KEY_PREFIX = "hash:";

    @Unique
    private final List<String> betterLecterns$rememberedBookKeys = new ArrayList<>();

    @Unique
    private final List<Integer> betterLecterns$rememberedPages = new ArrayList<>();

    @Unique
    private HolderLookup.Provider betterLecterns$registryLookup;

    @Shadow
    @SuppressWarnings("null")
    private @NonNull ItemStack book;

    @Shadow
    private int page;

    @Shadow
    public abstract void setBook(@NonNull ItemStack book);

    @Inject(method = "setBook(Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private void betterLecterns$restoreBookPage(@NonNull ItemStack book, CallbackInfo ci) {
        if (isBook(this.book)) {
            HolderLookup.Provider lookup = this.betterLecterns$getRegistryLookup();
            String bookId = this.betterLecterns$ensureBookId(this.book, lookup);
            int rememberedPage = this.betterLecterns$findRememberedPage(bookId);
            if (rememberedPage < 0 && lookup != null) {
                rememberedPage = this.betterLecterns$migrateLegacyPage(this.book, bookId, lookup);
            }
            if (rememberedPage >= 0) {
                this.page = rememberedPage;
                ((BlockEntity) (Object) this).setChanged();
            }
        }
    }

    @Inject(method = "setPage", at = @At("TAIL"))
    private void betterLecterns$rememberPage(int page, CallbackInfo ci) {
        this.betterLecterns$rememberCurrentBook();
        ((BlockEntity) (Object) this).setChanged();
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    @SuppressWarnings("null")
    private void betterLecterns$loadBookPageHistory(@NonNull ValueInput input, CallbackInfo ci) {
        this.betterLecterns$registryLookup = input.lookup();
        this.betterLecterns$rememberedBookKeys.clear();
        this.betterLecterns$rememberedPages.clear();

        for (ValueInput entry : input.childrenListOrEmpty(BOOK_PAGE_HISTORY_KEY)) {
            int rememberedPage = entry.getIntOr("Page", -1);
            if (rememberedPage < 0) {
                continue;
            }

            String rememberedKey = entry.getStringOr("Key", "");
            if (rememberedKey.isEmpty()) {
                String rememberedHash = entry.getStringOr("Hash", "");
                if (!rememberedHash.isEmpty()) {
                    rememberedKey = HASH_KEY_PREFIX + rememberedHash;
                }
            }
            if (rememberedKey.isEmpty()) {
                ItemStack rememberedBook = entry.read("Book", ItemStack.CODEC).orElse(ItemStack.EMPTY);
                if (isBook(rememberedBook)) {
                    rememberedKey = HASH_KEY_PREFIX
                        + betterLecterns$bookHash(rememberedBook, this.betterLecterns$registryLookup);
                }
            }

            if (!rememberedKey.isEmpty()) {
                this.betterLecterns$rememberBookKey(rememberedKey, rememberedPage);
            }
        }

        if (isBook(this.book)) {
            String bookId = this.betterLecterns$ensureBookId(this.book, this.betterLecterns$registryLookup);
            int rememberedPage = this.betterLecterns$findRememberedPage(bookId);
            if (rememberedPage < 0) {
                rememberedPage = this.betterLecterns$migrateLegacyPage(
                    this.book,
                    bookId,
                    this.betterLecterns$registryLookup
                );
            }
            if (rememberedPage >= 0) {
                this.page = rememberedPage;
            }
            ((BlockEntity) (Object) this).setChanged();
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void betterLecterns$saveBookPageHistory(@NonNull ValueOutput output, CallbackInfo ci) {
        ValueOutput.ValueOutputList entries = output.childrenList(BOOK_PAGE_HISTORY_KEY);
        for (int i = 0; i < this.betterLecterns$rememberedBookKeys.size(); i++) {
            ValueOutput entry = entries.addChild();
            entry.putString("Key", this.betterLecterns$rememberedBookKeys.get(i));
            entry.putInt("Page", this.betterLecterns$rememberedPages.get(i));
        }
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.book.isEmpty();
    }

    @Override
    public @NonNull ItemStack getItem(int slot) {
        return slot == 0 ? this.book : ItemStack.EMPTY;
    }

    @Override
    public @NonNull ItemStack removeItem(int slot, int count) {
        if (slot != 0 || count <= 0 || this.book.isEmpty()) {
            return ItemStack.EMPTY;
        }

        this.betterLecterns$rememberCurrentBook();
        ItemStack removed = this.book.split(count);
        if (this.book.isEmpty()) {
            this.setStack(ItemStack.EMPTY);
        } else {
            ((BlockEntity) (Object) this).setChanged();
        }
        return removed;
    }

    @Override
    public @NonNull ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0 || this.book.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = this.book.copy();
        this.setStack(ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int slot, @NonNull ItemStack stack) {
        if (slot == 0 && (stack.isEmpty() || this.isValid(slot, stack))) {
            this.setStack(stack);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return this.canPlayerUse(player) && !this.book.isEmpty();
    }

    @Override
    public boolean canPlaceItem(int slot, @NonNull ItemStack stack) {
        return this.isValid(slot, stack);
    }

    @Override
    public @NonNull ItemStack getStack() {
        return this.book;
    }

    @Override
    public void setStack(@NonNull ItemStack stack) {
        if (!stack.isEmpty() && !isBook(stack)) {
            return;
        }

        if (!stack.isEmpty() && !this.book.isEmpty()) {
            return;
        }

        if (stack.isEmpty()) {
            this.betterLecterns$rememberCurrentBook();
        }

        ItemStack book = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        this.setBook(book);

        BlockEntity blockEntity = (BlockEntity) (Object) this;
        Level level = blockEntity.getLevel();
        if (level != null) {
            boolean hasBook = !book.isEmpty();
            BlockState currentState = blockEntity.getBlockState();
            if (currentState.getValue(LecternBlock.HAS_BOOK) != hasBook) {
                LecternBlock.resetBookState(null, level, blockEntity.getBlockPos(), currentState, hasBook);
            }
        }
    }

    @Override
    public boolean isValid(int slot, @NonNull ItemStack stack) {
        return slot == 0 && this.book.isEmpty() && isBook(stack);
    }

    @Override
    public boolean canPlayerUse(@NonNull Player player) {
        return Container.stillValidBlockEntity((BlockEntity) (Object) this, player);
    }

    private static boolean isBook(@NonNull ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) || stack.is(Items.WRITABLE_BOOK);
    }

    @Unique
    private void betterLecterns$rememberCurrentBook() {
        if (isBook(this.book)) {
            String bookId = this.betterLecterns$ensureBookId(this.book, this.betterLecterns$getRegistryLookup());
            this.betterLecterns$rememberBookId(bookId, this.page);
        }
    }

    @Unique
    private void betterLecterns$rememberBookId(String bookId, int page) {
        this.betterLecterns$rememberBookKey(UUID_KEY_PREFIX + bookId, page);
    }

    @Unique
    private void betterLecterns$rememberBookKey(String key, int page) {
        int index = this.betterLecterns$rememberedBookKeys.indexOf(key);
        if (index >= 0) {
            this.betterLecterns$rememberedPages.set(index, page);
        } else {
            this.betterLecterns$rememberedBookKeys.add(key);
            this.betterLecterns$rememberedPages.add(page);
        }
    }

    @Unique
    private int betterLecterns$findRememberedPage(String bookId) {
        int index = this.betterLecterns$rememberedBookKeys.indexOf(UUID_KEY_PREFIX + bookId);
        return index >= 0 ? this.betterLecterns$rememberedPages.get(index) : -1;
    }

    @Unique
    private int betterLecterns$migrateLegacyPage(
        @NonNull ItemStack stack,
        String bookId,
        HolderLookup.Provider lookup
    ) {
        String legacyKey = HASH_KEY_PREFIX + betterLecterns$bookHash(stack, lookup);
        int index = this.betterLecterns$rememberedBookKeys.indexOf(legacyKey);
        if (index < 0) {
            return -1;
        }

        int rememberedPage = this.betterLecterns$rememberedPages.get(index);
        this.betterLecterns$rememberedBookKeys.remove(index);
        this.betterLecterns$rememberedPages.remove(index);
        this.betterLecterns$rememberBookId(bookId, rememberedPage);
        return rememberedPage;
    }

    @Unique
    private String betterLecterns$ensureBookId(@NonNull ItemStack stack, HolderLookup.Provider lookup) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        String bookId = customData.copyTag().getStringOr(BOOK_ID_KEY, "");
        try {
            UUID.fromString(bookId);
            return bookId;
        } catch (IllegalArgumentException exception) {
            String legacyKey = null;
            if (lookup != null && !this.betterLecterns$rememberedBookKeys.isEmpty()) {
                legacyKey = HASH_KEY_PREFIX + betterLecterns$bookHash(stack, lookup);
            }

            bookId = UUID.randomUUID().toString();
            String newBookId = bookId;
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(BOOK_ID_KEY, newBookId));

            if (legacyKey != null) {
                int index = this.betterLecterns$rememberedBookKeys.indexOf(legacyKey);
                if (index >= 0) {
                    int rememberedPage = this.betterLecterns$rememberedPages.get(index);
                    this.betterLecterns$rememberedBookKeys.remove(index);
                    this.betterLecterns$rememberedPages.remove(index);
                    this.betterLecterns$rememberBookId(bookId, rememberedPage);
                }
            }
            ((BlockEntity) (Object) this).setChanged();
            return bookId;
        }
    }

    @Unique
    private HolderLookup.Provider betterLecterns$getRegistryLookup() {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        Level level = blockEntity.getLevel();
        if (level != null) {
            this.betterLecterns$registryLookup = level.registryAccess();
        }
        return this.betterLecterns$registryLookup;
    }

    @Unique
    private static String betterLecterns$bookHash(@NonNull ItemStack stack, HolderLookup.Provider lookup) {
        JsonElement encoded = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, lookup), stack)
            .getOrThrow(message -> new IllegalStateException("Could not encode lectern book for page history: " + message));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}