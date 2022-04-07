/*
 * Copyright (c) 2019-2022 Team Galacticraft
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

package dev.galacticraft.mod.block.entity;

import dev.galacticraft.api.block.entity.MachineBlockEntity;
import dev.galacticraft.api.gas.GasVariant;
import dev.galacticraft.api.gas.Gases;
import dev.galacticraft.api.machine.MachineStatus;
import dev.galacticraft.api.machine.MachineStatuses;
import dev.galacticraft.api.machine.storage.MachineGasStorage;
import dev.galacticraft.api.machine.storage.MachineItemStorage;
import dev.galacticraft.api.machine.storage.display.ItemSlotDisplay;
import dev.galacticraft.api.machine.storage.display.TankDisplay;
import dev.galacticraft.api.transfer.v1.gas.GasStorage;
import dev.galacticraft.mod.Galacticraft;
import dev.galacticraft.mod.machine.GalacticraftMachineStatus;
import dev.galacticraft.mod.machine.storage.io.GalacticraftSlotTypes;
import dev.galacticraft.mod.screen.GalacticraftScreenHandlerType;
import dev.galacticraft.mod.util.FluidUtil;
import dev.galacticraft.mod.util.GenericStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="https://github.com/TeamGalacticraft">TeamGalacticraft</a>
 */
public class OxygenDecompressorBlockEntity extends MachineBlockEntity {
    public static final long MAX_OXYGEN = FluidUtil.bucketsToDroplets(50);
    public static final int CHARGE_SLOT = 0;
    public static final int OXYGEN_TANK_SLOT = 1;
    public static final int OXYGEN_TANK = 0;

    public OxygenDecompressorBlockEntity(BlockPos pos, BlockState state) {
        super(GalacticraftBlockEntityType.OXYGEN_DECOMPRESSOR, pos, state);
    }

    @Override
    protected MachineItemStorage createInventory() {
        return MachineItemStorage.Builder.create()
                .addSlot(GalacticraftSlotTypes.ENERGY_CHARGE, new ItemSlotDisplay(8, 62))
                .addSlot(GalacticraftSlotTypes.OXYGEN_TANK_FILL, new ItemSlotDisplay(80, 27))
                .build();
    }

    @Override
    protected MachineGasStorage createGasStorage() {
        return MachineGasStorage.Builder.create().addSlot(GalacticraftSlotTypes.OXYGEN_OUTPUT, MAX_OXYGEN, new TankDisplay(31, 8, 48)).build();
    }

    @Override
    protected @NotNull MachineStatus tick() {
        this.attemptChargeFromStack(CHARGE_SLOT);
        this.trySpreadGases();
        Storage<GasVariant> gasStorage = ContainerItemContext.ofSingleSlot(this.itemStorage().getSlot(OXYGEN_TANK_SLOT)).find(GasStorage.ITEM);
        if (gasStorage == null) return GalacticraftMachineStatus.MISSING_OXYGEN_TANK;
        if (gasStorage.simulateExtract(GasVariant.of(Gases.OXYGEN), Long.MAX_VALUE, null) == 0) return GalacticraftMachineStatus.EMPTY_OXYGEN_TANK;
        try (Transaction transaction = Transaction.openOuter()) {
            if (this.energyStorage().extract(Galacticraft.CONFIG_MANAGER.get().oxygenDecompressorEnergyConsumptionRate(), transaction) == Galacticraft.CONFIG_MANAGER.get().oxygenDecompressorEnergyConsumptionRate()) {
                GenericStorageUtil.move(GasVariant.of(Gases.OXYGEN), gasStorage, this.gasStorage(), Long.MAX_VALUE, transaction);
                transaction.commit();
                return GalacticraftMachineStatus.DECOMPRESSING;
            } else {
                return MachineStatuses.NOT_ENOUGH_ENERGY;
            }
        }
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        if (this.security().hasAccess(player)) return GalacticraftScreenHandlerType.create(GalacticraftScreenHandlerType.OXYGEN_DECOMPRESSOR_HANDLER, syncId, inv, this);
        return null;
    }
}