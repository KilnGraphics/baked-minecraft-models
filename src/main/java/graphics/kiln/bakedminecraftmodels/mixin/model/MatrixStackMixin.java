/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.model;

import graphics.kiln.bakedminecraftmodels.access.BatchContainer;
import graphics.kiln.bakedminecraftmodels.data.InstanceBatch;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MatrixStack.class)
public class MatrixStackMixin implements BatchContainer {

    private InstanceBatch batch;

    @Override
    public InstanceBatch getBatch() {
        return batch;
    }

    @Override
    public void setBatch(InstanceBatch batch) {
        this.batch = batch;
    }
}
