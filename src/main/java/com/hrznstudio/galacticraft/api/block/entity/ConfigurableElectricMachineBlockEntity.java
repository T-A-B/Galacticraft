/*
 * Copyright (c) 2019 HRZN LTD
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.hrznstudio.galacticraft.api.block.entity;

import alexiil.mc.lib.attributes.item.FixedItemInv;
import alexiil.mc.lib.attributes.item.ItemInvSlotChangeListener.ItemInvSlotListener;
import alexiil.mc.lib.attributes.item.LimitedFixedItemInv;
import alexiil.mc.lib.attributes.item.filter.ConstantItemFilter;
import alexiil.mc.lib.attributes.item.filter.ItemFilter;
import alexiil.mc.lib.attributes.item.impl.FullFixedItemInv;
import com.hrznstudio.galacticraft.Galacticraft;
import com.hrznstudio.galacticraft.api.block.ConfigurableElectricMachineBlock;
import com.hrznstudio.galacticraft.api.block.SideOption;
import com.hrznstudio.galacticraft.api.internal.data.MinecraftServerTeamsGetter;
import com.hrznstudio.galacticraft.energy.GalacticraftEnergy;
import io.github.cottonmc.component.UniversalComponents;
import io.github.cottonmc.component.api.ActionType;
import io.github.cottonmc.component.energy.CapacitorComponent;
import io.github.cottonmc.component.energy.CapacitorComponentHelper;
import io.github.cottonmc.component.energy.impl.SimpleCapacitorComponent;
import nerdhub.cardinal.components.api.ComponentType;
import nerdhub.cardinal.components.api.component.BlockComponentProvider;
import nerdhub.cardinal.components.api.component.Component;
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author <a href="https://github.com/StellarHorizons">StellarHorizons</a>
 */
public abstract class ConfigurableElectricMachineBlockEntity extends BlockEntity implements BlockEntityClientSerializable, BlockComponentProvider {

    private final FullFixedItemInv inventory = new FullFixedItemInv(getInvSize()) {
        @Override
        public boolean isItemValidForSlot(int slot, ItemStack item) {
            return getFilterForSlot(slot).matches(item);
        }

        @Override
        public ItemFilter getFilterForSlot(int slot) {
            return ConfigurableElectricMachineBlockEntity.this.getFilterForSlot(slot);
        }
    };

    private final SimpleCapacitorComponent capacitorComponent = new SimpleCapacitorComponent(getMaxEnergy(), GalacticraftEnergy.GALACTICRAFT_JOULES) {
        @Override
        public boolean canExtractEnergy() {
            return ConfigurableElectricMachineBlockEntity.this.canExtractEnergy();
        }

        @Override
        public boolean canInsertEnergy() {
            return ConfigurableElectricMachineBlockEntity.this.canInsertEnergy();
        }
    };

    protected abstract boolean canExtractEnergy();

    protected abstract boolean canInsertEnergy();

    private final LimitedFixedItemInv limitedInventory = inventory.createLimitedFixedInv();
    private final FixedItemInv exposedInventory = limitedInventory.asUnmodifiable();

    private final SecurityInfo security = new SecurityInfo();
    private RedstoneState redstoneState = RedstoneState.DISABLED;

    public ConfigurableElectricMachineBlockEntity(BlockEntityType<? extends ConfigurableElectricMachineBlockEntity> blockEntityType) {
        super(blockEntityType);
        capacitorComponent.getListeners().add(this::markDirty);
        this.inventory.setOwnerListener((ItemInvSlotListener) (inv, slot) -> markDirty());
    }

    public RedstoneState getRedstoneState() {
        assert redstoneState != null;
        return redstoneState;
    }

    public void setRedstoneState(RedstoneState redstoneState) {
        if (redstoneState != null) {
            this.redstoneState = redstoneState;
        }
    }

    public MachineStatus getStatusForTooltip() {
        return null;
    }

    /**
     * Whether the current machine is enabled
     *
     * @return The state of the machine
     */
    public boolean disabled() {
        switch (this.redstoneState) {
            case OFF:
                return this.getWorld().isReceivingRedstonePower(pos);
            case ON:
                return !this.getWorld().isReceivingRedstonePower(pos);
            default:
                return false;
        }
    }

    @Override
    public <T extends Component> boolean hasComponent(BlockView blockView, BlockPos pos, ComponentType<T> type, @Nullable Direction side) {
        if (type == UniversalComponents.CAPACITOR_COMPONENT) {
            BlockState state = blockView.getBlockState(pos);
            SideOption option = ((ConfigurableElectricMachineBlock) state.getBlock()).getOption(state, ConfigurableElectricMachineBlock.BlockFace.toFace(state.get(ConfigurableElectricMachineBlock.FACING), side));
            return option == SideOption.POWER_INPUT || option == SideOption.POWER_OUTPUT;
        }
        return false;
    }

    @Nullable
    @Override
    public <T extends Component> T getComponent(BlockView blockView, BlockPos pos, ComponentType<T> type, @Nullable Direction side) {
        if (type == UniversalComponents.CAPACITOR_COMPONENT) {
            BlockState state = blockView.getBlockState(pos);
            SideOption option = ((ConfigurableElectricMachineBlock) state.getBlock()).getOption(state, ConfigurableElectricMachineBlock.BlockFace.toFace(state.get(ConfigurableElectricMachineBlock.FACING), side));
            if (option == SideOption.POWER_INPUT || option == SideOption.POWER_OUTPUT) {
                SimpleCapacitorComponent cc = new SimpleCapacitorComponent(capacitorComponent.getMaxEnergy(), GalacticraftEnergy.GALACTICRAFT_JOULES) {
                    @Override
                    public boolean canExtractEnergy() {
                        return option == SideOption.POWER_OUTPUT;
                    }

                    @Override
                    public boolean canInsertEnergy() {
                        return option == SideOption.POWER_INPUT;
                    }
                };

                cc.fromTag(capacitorComponent.toTag(new CompoundTag()));
                cc.getListeners().add(() -> capacitorComponent.setCurrentEnergy(cc.getCurrentEnergy()));
                //noinspection unchecked
                return (T) cc;
            }
        }
        return null;
    }

    @Override
    public Set<ComponentType<?>> getComponentTypes(BlockView blockView, BlockPos pos, @Nullable Direction side) {
        Set<ComponentType<?>> set = new HashSet<>();
        BlockState state = blockView.getBlockState(pos);
        SideOption option = ((ConfigurableElectricMachineBlock) state.getBlock()).getOption(state, ConfigurableElectricMachineBlock.BlockFace.toFace(state.get(ConfigurableElectricMachineBlock.FACING), side));
        if (option == SideOption.POWER_OUTPUT || option == SideOption.POWER_INPUT) {
            set.add(UniversalComponents.CAPACITOR_COMPONENT);
        }
        return set;
    }

    /**
     * The max energy that this machine can hold. Override for machines that should hold more.
     *
     * @return Energy capacity of this machine.
     */
    public int getMaxEnergy() {
        return Galacticraft.configManager.get().machineEnergyStorageSize();
    }

    /**
     * @return The {@link ItemFilter} for the given slot of {@link #getInventory()}.
     */
    protected ItemFilter getFilterForSlot(int slot) {
        return ConstantItemFilter.ANYTHING;
    }

    /**
     * @return The maximum amount of energy that can be transferred to or from a battery in this machine per call to
     * {@link #attemptChargeFromStack(int)} or {@link #attemptDrainPowerToStack(int)}
     */
    protected int getBatteryTransferRate() {
        return 50;
    }

    public SimpleCapacitorComponent getCapacitatorComponent() {
        return capacitorComponent;
    }

    /**
     * Tries to charge this machine from the item in the given slot in this {@link #getInventory}.
     */
    protected void attemptChargeFromStack(int slot) {
        if (getCapacitatorComponent().getCurrentEnergy() >= getCapacitatorComponent().getMaxEnergy()) {
            return;
        }
        ItemStack stack = inventory.getInvStack(slot).copy();
        int neededEnergy = Math.min(getBatteryTransferRate(), getCapacitatorComponent().getMaxEnergy() - getCapacitatorComponent().getCurrentEnergy());
        if (GalacticraftEnergy.isEnergyItem(stack)) {
            int amountFailedToExtract = GalacticraftEnergy.extractEnergy(stack, neededEnergy, ActionType.PERFORM);
            this.getCapacitatorComponent().insertEnergy(GalacticraftEnergy.GALACTICRAFT_JOULES, neededEnergy - amountFailedToExtract, ActionType.PERFORM);
            inventory.forceSetInvStack(slot, stack);
        }
    }

    public final boolean canUse(PlayerEntity player) {
        ConfigurableElectricMachineBlockEntity.SecurityInfo security = this.getSecurity();
        return security.hasAccess(player);
    }

    /**
     * Tries to drain some of this machine's power into the item in the given slot in this {@link #getInventory}.
     *
     * @param slot The slot id of the item
     */
    protected void attemptDrainPowerToStack(int slot) {
        int available = Math.min(getBatteryTransferRate(), getCapacitatorComponent().getCurrentEnergy());
        if (available <= 0) {
            return;
        }
        ItemStack stack = inventory.getInvStack(slot).copy();
        if (GalacticraftEnergy.isEnergyItem(stack)) {
            if (GalacticraftEnergy.getEnergy(stack) < GalacticraftEnergy.getMaxEnergy(stack)) {
                int i = GalacticraftEnergy.insertEnergy(stack, available, ActionType.PERFORM);
                this.getCapacitatorComponent().extractEnergy(GalacticraftEnergy.GALACTICRAFT_JOULES, available - i, ActionType.PERFORM);
                inventory.forceSetInvStack(slot, stack);
            }
        }
    }

    protected abstract int getInvSize();

    public final FullFixedItemInv getInventory() {
        return inventory;
    }

    /**
     * @return A {@link LimitedFixedItemInv} that can be used to limit what neighbouring blocks do with the
     * {@link #getExposedInventory() exposed inventory}.
     */
    public final LimitedFixedItemInv getLimitedInventory() {
        return limitedInventory;
    }

    /**
     * @return The {@link FixedItemInv} that is exposed to neighbouring blocks via attributes.
     */
    public final FixedItemInv getExposedInventory() {
        return exposedInventory;
    }

    @Nonnull
    public SecurityInfo getSecurity() {
        return security;
    }

    @Override
    public CompoundTag toTag(CompoundTag tag) {
        super.toTag(tag);
        tag.putInt("Energy", getCapacitatorComponent().getCurrentEnergy());
        tag.put("Inventory", inventory.toTag());
        this.security.toTag(tag);
        tag.putString("Redstone", redstoneState.asString());
        return tag;
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag) {
        super.fromTag(state, tag);
        getCapacitatorComponent().setCurrentEnergy(tag.getInt("Energy"));
        inventory.fromTag(tag.getCompound("Inventory"));
        this.security.fromTag(tag);
        redstoneState = RedstoneState.fromString(tag.getString("Redstone"));
    }

    @Override
    public void fromClientTag(CompoundTag tag) {
        this.fromTag(this.getCachedState(), tag);
    }

    @Override
    public CompoundTag toClientTag(CompoundTag tag) {
        return this.toTag(tag);
    }

    public void trySpreadEnergy() {
        BlockState state = world.getBlockState(pos);
        for (ConfigurableElectricMachineBlock.BlockFace face : ConfigurableElectricMachineBlock.BlockFace.values()) {
            SideOption option = state.get(((ConfigurableElectricMachineBlock) state.getBlock()).getProperty(face));
            if (option == SideOption.POWER_INPUT || option == SideOption.POWER_OUTPUT) {
                Direction direction = face.toDirection(state.get(Properties.HORIZONTAL_FACING));
                BlockState other = world.getBlockState(pos.offset(direction));

                CapacitorComponent component = CapacitorComponentHelper.INSTANCE.getComponent(world, pos.offset(direction), direction.getOpposite()); //

                if (option == SideOption.POWER_INPUT && ((ConfigurableElectricMachineBlock) other.getBlock()).getOption(other, face.getOpposite()) == SideOption.POWER_OUTPUT) {
                    if (component.canExtractEnergy() && component.getPreferredType().isCompatibleWith(getCapacitatorComponent().getPreferredType())) {
                        int extracted = component.extractEnergy(getCapacitatorComponent().getPreferredType(), Math.min(256, getCapacitatorComponent().getMaxEnergy() - getCapacitatorComponent().getCurrentEnergy()), ActionType.PERFORM);
                        getCapacitatorComponent().insertEnergy(getCapacitatorComponent().getPreferredType(), extracted, ActionType.PERFORM);
                    }
                } else {
                    if (component.canInsertEnergy() && component.getPreferredType().isCompatibleWith(getCapacitatorComponent().getPreferredType()) && ((ConfigurableElectricMachineBlock) other.getBlock()).getOption(other, face.getOpposite()) == SideOption.POWER_INPUT) {
                        int extracted = getCapacitatorComponent().extractEnergy(getCapacitatorComponent().getPreferredType(), Math.min(256, component.getMaxEnergy() - component.getCurrentEnergy()), ActionType.PERFORM);
                        component.insertEnergy(getCapacitatorComponent().getPreferredType(), extracted, ActionType.PERFORM);
                    }
                }

            }
        }
    }

    public void idleEnergyDecrement(boolean off) {
        if (getEnergyUsagePerTick() > 0 && getEnergyUsagePerTick() / 20 > 0) {
            if (GalacticraftEnergy.Values.getTick() % ((75 * (getEnergyUsagePerTick() / 20)) * (off ? 2 : 1)) == 0) {
                getCapacitatorComponent().extractEnergy(GalacticraftEnergy.GALACTICRAFT_JOULES, 1, ActionType.PERFORM);
            }
        }
    }

    public abstract int getEnergyUsagePerTick();

    public enum RedstoneState implements StringIdentifiable {
        /**
         * Ignores redstone entirely.
         */
        DISABLED,

        /**
         * When powered with redstone, the machine turns off.
         */
        OFF,

        /**
         * When powered with redstone, the machine turns on.
         */
        ON;

        public static RedstoneState fromString(String string) {
            switch (string.toUpperCase()) {
                case "OFF":
                    return OFF;
                case "ON":
                    return ON;
                default:
                    return DISABLED;
            }
        }

        @Override
        public String asString() {
            return this.name().toLowerCase();
        }
    }

    public interface MachineStatus {
        Text getText();
    }

    public static class SecurityInfo {
        private UUID owner;
        private String username;
        private Identifier team;
        private Publicity publicity;

        protected SecurityInfo() {
            this.owner = null;
            this.publicity = Publicity.PUBLIC;
            this.team = null;
            this.username = "";
        }

        public boolean isOwner(PlayerEntity player) {
            return isOwner(player.getUuid());
        }

        public boolean isOwner(UUID uuid) {
            if (owner == null) owner = uuid;
            return this.owner.equals(uuid);
        }

        public boolean hasAccess(PlayerEntity player) {
            switch (publicity) {
                case PUBLIC:
                    return true;
                case SPACE_RACE:
                    return (((MinecraftServerTeamsGetter) player.getServer()).getSpaceRaceTeams().getTeam(player.getUuid()) != null)
                            && ((MinecraftServerTeamsGetter) player.getServer()).getSpaceRaceTeams().getTeam(player.getUuid()).players.containsKey(owner);
                case PRIVATE:
                    return isOwner(player);
            }
            return false;
        }

        public Publicity getPublicity() {
            return publicity;
        }

        public void setPublicity(Publicity publicity) {
            this.publicity = publicity;
        }

        public boolean hasOwner() {
            return this.owner != null;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public UUID getOwner() {
            return this.owner;
        }

        public void setOwner(PlayerEntity owner) {
            if (!this.hasOwner()) {
                this.owner = owner.getUuid();
            }
            this.username = owner.getEntityName();
        }

        public Identifier getTeam() {
            return team;
        }

        public boolean hasTeam() {
            return team != null;
        }

        public CompoundTag toTag(CompoundTag tag) {
            CompoundTag compoundTag = new CompoundTag();
            if (this.hasOwner()) {
                compoundTag.putUuid("owner", this.owner);
            }
            compoundTag.putString("username", this.username);
            compoundTag.putString("publicity", this.publicity.asString());
            if (this.hasTeam()) {
                compoundTag.putString("team", team.toString());
            }
            tag.put("security", compoundTag);
            return tag;
        }

        public void fromTag(CompoundTag tag) {
            CompoundTag compoundTag = tag.getCompound("security");

            if (compoundTag.contains("owner")) {
                if (!this.hasOwner()) {
                    this.owner = compoundTag.getUuid("owner");
                }
            }

            if (compoundTag.contains("team")) {
                if (!this.hasTeam()) {
                    this.team = new Identifier(compoundTag.getString("team"));
                }
            }

            this.username = compoundTag.getString("username");
            this.publicity = Publicity.valueOf(compoundTag.getString("publicity"));
        }


        public enum Publicity implements StringIdentifiable {
            PUBLIC,
            SPACE_RACE,
            PRIVATE;

            @Override
            public String asString() {
                return this.toString();
            }
        }
    }
}
