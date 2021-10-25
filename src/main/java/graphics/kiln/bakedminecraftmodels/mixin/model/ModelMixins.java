/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.model;

import graphics.kiln.bakedminecraftmodels.BakedMinecraftModelsRenderLayerManager;
import graphics.kiln.bakedminecraftmodels.access.ModelContainer;
import graphics.kiln.bakedminecraftmodels.access.RenderLayerContainer;
import graphics.kiln.bakedminecraftmodels.data.MatrixEntryList;
import graphics.kiln.bakedminecraftmodels.mixin.buffer.VertexBufferAccessor;
import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

@Mixin({AnimalModel.class,
        BookModel.class, // TODO OPT: inject into renderBook instead of render so it works on block models
        CompositeEntityModel.class,
        EnderDragonEntityRenderer.DragonEntityModel.class,
        DragonHeadEntityModel.class,
        LlamaEntityModel.class,
        RabbitEntityModel.class,
        ShieldEntityModel.class,
        SignBlockEntityRenderer.SignModel.class,
        SinglePartEntityModel.class,
        SkullEntityModel.class,
        TintableAnimalModel.class,
        TintableCompositeModel.class,
        TridentEntityModel.class, // FIXME: enchantment glint uses dual
        TurtleEntityModel.class
})
public class ModelMixins implements VboBackedModel {

    @Unique
    @Nullable
    private VertexBuffer bmm$bakedVertices;

    @Unique
    private MatrixEntryList bmm$currentMatrices;

    @Unique
    private ByteBuffer bmm$bakedIndexData;

    @Unique
    private VboBackedModel.BakedIndexMetadata bmm$bakedIndexMetadata;

    @Override
    @Unique
    public VertexBuffer getBakedVertices() {
        return bmm$bakedVertices;
    }

    @Override
    @Unique
    public ByteBuffer getBakedIndexData() {
        return bmm$bakedIndexData;
    }

    @Override
    @Unique
    public BakedIndexMetadata getBakedIndexMetadata() {
        return bmm$bakedIndexMetadata;
    }

    @Override
    public MatrixEntryList getCurrentMatrices() {
        return bmm$currentMatrices;
    }

    @Override
    public void setMatrixEntryList(MatrixEntryList matrixEntryList) {
        bmm$currentMatrices = matrixEntryList;
    }

    @Unique
    private boolean bmm$currentPassBakeable;

    @Unique
    private VertexFormat.DrawMode bmm$drawMode;

    @Unique
    private VertexFormat bmm$vertexFormat;

    @Unique
    private RenderLayer bmm$convertedRenderLayer;

    @Unique
    private MatrixStack.Entry bmm$baseMatrix;

    @Unique
    private VboBackedModel bmm$previousStoredModel;

    @Unique
    protected boolean bmm$childBakeable() { // this will be overridden by the lowest in the hierarchy as long as it's not private
        return bmm$currentPassBakeable;
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private void updateCurrentPass(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (!bmm$childBakeable() && GlobalModelUtils.getNestedBufferBuilder(vertexConsumer) instanceof RenderLayerContainer renderLayerContainer) {
            RenderLayer convertedRenderLayer = BakedMinecraftModelsRenderLayerManager.tryDeriveSmartRenderLayer(renderLayerContainer.getRenderLayer());
            bmm$currentPassBakeable = convertedRenderLayer != null && MinecraftClient.getInstance().getWindow() != null;
            if (bmm$currentPassBakeable) {
                bmm$drawMode = convertedRenderLayer.getDrawMode();
                bmm$vertexFormat = convertedRenderLayer.getVertexFormat();
                bmm$convertedRenderLayer = convertedRenderLayer;
                bmm$baseMatrix = matrices.peek();
                ModelContainer modelContainer = (ModelContainer) matrices;
                bmm$previousStoredModel = modelContainer.getModel();
                modelContainer.setModel(this);
            }
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private VertexConsumer changeVertexConsumer(VertexConsumer existingConsumer) {
        if (getBakedVertices() != null && bmm$currentPassBakeable) {
            return null;
        } else if (bmm$currentPassBakeable) {
            GlobalModelUtils.VBO_BUFFER_BUILDER.begin(bmm$drawMode, bmm$vertexFormat); // FIXME: not thread safe, could use a lock around it but may freeze program if nested model
            return GlobalModelUtils.VBO_BUFFER_BUILDER;
        } else {
            return existingConsumer;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    private void createVbo(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (getBakedVertices() == null && bmm$currentPassBakeable) {
            GlobalModelUtils.VBO_BUFFER_BUILDER.end();
            bmm$bakedVertices = new VertexBuffer();
            getBakedVertices().upload(GlobalModelUtils.VBO_BUFFER_BUILDER.getInternalBufferBuilder());
            GlobalModelUtils.bakingData.addCloseable(bmm$bakedVertices);

            // If we're transparent then we need to grab the EBO contents
            // Yes, reading the data back from the GPU is a kinda horrible way of doing this, but none of the alternatives
            // look particularly appealing either. Since we're only doing this once per at startup it should be fine.
            // TODO only do this if we're actually transparent, otherwise it's a waste of memory
            VertexBufferAccessor vba = (VertexBufferAccessor) getBakedVertices();
            int eboSize = vba.getVertexFormat().size * vba.getVertexCount(); // Mappings are wrong, should be 'index' not 'vertex'
            ByteBuffer eboData = MemoryUtil.memAlloc(eboSize);
            getBakedVertices().bind();
            GL20.glGetBufferSubData(GL20.GL_ELEMENT_ARRAY_BUFFER, 0, eboData);
            eboData.limit(eboSize);
            VertexBuffer.unbind();

            // Note that this completely breaks triangle fans/strips/any other primitive type that depends on the
            // adjacent primitives, but that'd be a bit ridiculous to implement.
            int paddedIndicesLen = MathHelper.roundUpToMultiple(eboData.remaining(), 4);
            int vecLen = 3 * 4;
            int indexCount = vba.getVertexCount();
            int vertPerPrim = 3; // Actually, quads are turned into triangles internally - was vba.getDrawMode().size;
            int primitiveCount = indexCount / vertPerPrim;
            FloatBuffer vertexPositions = GlobalModelUtils.VBO_BUFFER_BUILDER.getVertexPositions();
            bmm$bakedIndexData = ByteBuffer.allocate(paddedIndicesLen + vecLen * primitiveCount);
            bmm$bakedIndexMetadata = new BakedIndexMetadata(vba.getVertexFormat(), vba.getDrawMode(), indexCount, primitiveCount, paddedIndicesLen);
            bmm$bakedIndexData.put(eboData);
            bmm$bakedIndexData.position(paddedIndicesLen);
            eboData.position(0); // Go back to the start after putting it into bakedIndexData
            for (int primitiveIdx = 0; primitiveIdx < primitiveCount; primitiveIdx++) {
                float x = 0, y = 0, z = 0;
                // Add each of the referenced verts
                for (int vertId = 0; vertId < vertPerPrim; vertId++) {
                    int idx = switch (vba.getVertexFormat()) {
                        case BYTE -> eboData.get();
                        case SHORT -> eboData.getShort();
                        case INT -> eboData.getInt();
                    };
                    vertexPositions.position(idx * 3); // 3=length of vec in floats (NOT bytes)
                    x += vertexPositions.get();
                    y += vertexPositions.get();
                    z += vertexPositions.get();
                }
                bmm$bakedIndexData.putFloat(x / vertPerPrim);
                bmm$bakedIndexData.putFloat(y / vertPerPrim);
                bmm$bakedIndexData.putFloat(z / vertPerPrim);
            }

            if (bmm$bakedIndexData.hasRemaining())
                throw new IllegalStateException("Didn't fill up index data");

            bmm$bakedIndexData.flip();

            MemoryUtil.memFree(eboData);
            GlobalModelUtils.VBO_BUFFER_BUILDER.clear();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    private void setModelInstanceData(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (bmm$currentPassBakeable) {
            GlobalModelUtils.bakingData.addInstance(this, bmm$convertedRenderLayer, bmm$baseMatrix, red, green, blue, alpha, overlay, light);
            bmm$currentPassBakeable = false; // we want this to be false by default when we start at the top again
            // reset variables that we don't need until next run
            bmm$drawMode = null;
            bmm$vertexFormat = null;
            bmm$convertedRenderLayer = null;
            bmm$baseMatrix = null;
            ((ModelContainer) matrices).setModel(bmm$previousStoredModel);
            bmm$previousStoredModel = null;
        }
    }

}
