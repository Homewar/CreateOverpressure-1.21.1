package com.hwmods.overpressure;

import java.util.List;

import com.hwmods.overpressure.transport.TransportEndpoint;
import com.hwmods.overpressure.transport.TubeGraphRoute;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CapsulePortBlockEntity extends SmartBlockEntity implements TransportEndpoint {
    private static final int SLOT_COUNT = PackageItem.SLOTS;
    private static final int DISPATCH_RETRY_INTERVAL = 5;

    private final ItemStackHandler contents = new ItemStackHandler(SLOT_COUNT) {
        @Override
        @SuppressWarnings("deprecation")
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.getItem().canFitInsideContainerItems();
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (!suppressInventoryUpdates) {
                onInventoryChanged();
            }
        }
    };
    private ItemStack loadedPackage = ItemStack.EMPTY;
    private ItemStack sealedPackage = ItemStack.EMPTY;
    private boolean suppressInventoryUpdates;

    public CapsulePortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAPSULE_PORT.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CapsulePortBlockEntity port) {
        if (!port.sealedPackage.isEmpty() && level.getGameTime() % DISPATCH_RETRY_INTERVAL == 0) {
            port.tryDispatch();
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean isSupportedCargo(ItemStack stack) {
        return !stack.isEmpty()
                && (PackageItem.isPackage(stack) || stack.getItem().canFitInsideContainerItems());
    }

    public boolean canLoadItem(ItemStack stack) {
        if (!isSupportedCargo(stack)
                || !sealedPackage.isEmpty()
                || !loadedPackage.isEmpty()
                || getBlockState().getValue(CapsulePortBlock.POWERED)) {
            return false;
        }
        return !PackageItem.isPackage(stack) || isInventoryEmpty();
    }

    public boolean canTakeItems() {
        return !getBlockState().getValue(CapsulePortBlock.POWERED)
                && (!sealedPackage.isEmpty() || !loadedPackage.isEmpty() || !isInventoryEmpty());
    }

    public int insertFromPlayer(ItemStack stack) {
        if (!canLoadItem(stack)) {
            return 0;
        }

        if (PackageItem.isPackage(stack)) {
            loadedPackage = stack.copyWithCount(1);
            onInventoryChanged();
            return 1;
        }

        ItemStack remaining = stack.copy();
        int originalCount = remaining.getCount();
        for (int slot = 0; slot < contents.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = contents.insertItem(slot, remaining, false);
        }
        return originalCount - remaining.getCount();
    }

    public ItemStack takeLastItem() {
        if (!canTakeItems()) {
            return ItemStack.EMPTY;
        }
        if (!sealedPackage.isEmpty()) {
            ItemStack result = sealedPackage;
            sealedPackage = ItemStack.EMPTY;
            onInventoryChanged();
            return result;
        }
        if (!loadedPackage.isEmpty()) {
            ItemStack result = loadedPackage;
            loadedPackage = ItemStack.EMPTY;
            onInventoryChanged();
            return result;
        }

        for (int slot = contents.getSlots() - 1; slot >= 0; slot--) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                return contents.extractItem(slot, stack.getCount(), false);
            }
        }
        return ItemStack.EMPTY;
    }

    public void sealAndDispatch() {
        if (level == null || level.isClientSide || !sealedPackage.isEmpty()
                || (loadedPackage.isEmpty() && isInventoryEmpty())) {
            return;
        }

        ItemStack packageStack;
        if (!loadedPackage.isEmpty()) {
            packageStack = loadedPackage;
            loadedPackage = ItemStack.EMPTY;
        } else {
            packageStack = PackageItem.containing(contents);
            suppressInventoryUpdates = true;
            for (int slot = 0; slot < contents.getSlots(); slot++) {
                contents.setStackInSlot(slot, ItemStack.EMPTY);
            }
            suppressInventoryUpdates = false;
        }

        String address = readSignAddress();
        if (!address.isBlank()) {
            PackageItem.addAddress(packageStack, address);
        }

        sealedPackage = packageStack;
        setOpen(false);
        setChangedAndSync();
        tryDispatch();
    }

    private boolean tryDispatch() {
        if (level == null || level.isClientSide || sealedPackage.isEmpty()) {
            return false;
        }

        Direction outputDirection = CapsulePortBlock.getOutputDirection(getBlockState());
        BlockPos firstPathPos = worldPosition.relative(outputDirection);
        if (!PneumaticLine.isPathNode(level, firstPathPos)) {
            return false;
        }

        TubePath path = TubeNetworkPathfinder.findPathToInsertConnector(level, worldPosition, firstPathPos);
        if (path.isEmpty()) {
            return false;
        }

        PneumaticTubeBlockEntity firstTube = findFirstTubeOnPath(path);
        if (firstTube == null || !firstTube.canAcceptItem(sealedPackage, path)) {
            return false;
        }

        ItemStack dispatched = sealedPackage;
        if (!firstTube.acceptItem(dispatched, path, worldPosition)) {
            return false;
        }

        sealedPackage = ItemStack.EMPTY;
        TubeNetworkPathfinder.commitDeviderChoices(level, path);
        setChangedAndSync();
        return true;
    }

    private PneumaticTubeBlockEntity findFirstTubeOnPath(TubePath path) {
        if (level == null) {
            return null;
        }
        for (BlockPos pathPos : path.tubePositions()) {
            BlockEntity blockEntity = level.getBlockEntity(pathPos);
            if (blockEntity instanceof PneumaticTubeBlockEntity tube) {
                return tube;
            }
        }
        return null;
    }

    private String readSignAddress() {
        if (level == null) {
            return "";
        }

        String address = "";
        for (Direction direction : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(direction));
            if (!(blockEntity instanceof SignBlockEntity sign)) {
                continue;
            }

            String signAddress = readSignSide(sign, false);
            if (signAddress.isBlank()) {
                signAddress = readSignSide(sign, true);
            }
            if (!signAddress.isBlank()) {
                address = signAddress;
            }
        }
        return address;
    }

    private static String readSignSide(SignBlockEntity sign, boolean front) {
        StringBuilder address = new StringBuilder();
        for (Component line : sign.getText(front).getMessages(false)) {
            String text = line.getString().trim();
            if (text.isBlank()) {
                continue;
            }
            if (!address.isEmpty()) {
                address.append(' ');
            }
            address.append(text);
        }
        return address.toString().trim();
    }

    public boolean hasVisibleCapsule() {
        return sealedPackage.isEmpty() && (!loadedPackage.isEmpty() || !isInventoryEmpty());
    }

    private boolean isInventoryEmpty() {
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            if (!contents.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void onInventoryChanged() {
        setOpen(sealedPackage.isEmpty() && (!loadedPackage.isEmpty() || !isInventoryEmpty()));
        setChangedAndSync();
    }

    private void setOpen(boolean open) {
        if (level == null || getBlockState().getValue(CapsulePortBlock.OPEN) == open) {
            return;
        }
        level.setBlock(worldPosition, getBlockState().setValue(CapsulePortBlock.OPEN, open),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) {
            sendData();
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }

        suppressInventoryUpdates = true;
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                net.minecraft.world.level.block.Block.popResource(level, worldPosition, stack.copy());
                contents.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        suppressInventoryUpdates = false;
        if (!loadedPackage.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition, loadedPackage.copy());
            loadedPackage = ItemStack.EMPTY;
        }
        if (!sealedPackage.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition, sealedPackage.copy());
            sealedPackage = ItemStack.EMPTY;
        }
        setChanged();
    }

    @Override
    public boolean canReceiveFrom(Level level, BlockPos sourcePos) {
        return false;
    }

    @Override
    public boolean allowsRoute(Level level, Direction travelDirection) {
        return CapsulePortBlock.getOutputDirection(getBlockState()) == travelDirection;
    }

    @Override
    public ItemStack insertCargo(Level level, ItemStack stack) {
        return stack;
    }

    @Override
    public TubeGraphRoute.NodeKind graphNodeKind() {
        return TubeGraphRoute.NodeKind.CAPSULE_PORT;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Contents", contents.serializeNBT(registries));
        if (!loadedPackage.isEmpty()) {
            tag.put("LoadedPackage", loadedPackage.save(registries));
        }
        if (!sealedPackage.isEmpty()) {
            tag.put("SealedPackage", sealedPackage.save(registries));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        suppressInventoryUpdates = true;
        if (tag.contains("Contents")) {
            contents.deserializeNBT(registries, tag.getCompound("Contents"));
        }
        loadedPackage = tag.contains("LoadedPackage")
                ? ItemStack.parseOptional(registries, tag.getCompound("LoadedPackage"))
                : ItemStack.EMPTY;
        sealedPackage = tag.contains("SealedPackage")
                ? ItemStack.parseOptional(registries, tag.getCompound("SealedPackage"))
                : ItemStack.EMPTY;
        suppressInventoryUpdates = false;
    }
}
