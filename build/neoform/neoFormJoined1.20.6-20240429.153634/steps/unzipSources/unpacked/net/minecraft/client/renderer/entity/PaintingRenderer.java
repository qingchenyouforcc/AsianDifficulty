package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.PaintingTextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PaintingRenderer extends EntityRenderer<Painting> {
    public PaintingRenderer(EntityRendererProvider.Context p_174332_) {
        super(p_174332_);
    }

    public void render(Painting p_115552_, float p_115553_, float p_115554_, PoseStack p_115555_, MultiBufferSource p_115556_, int p_115557_) {
        p_115555_.pushPose();
        p_115555_.mulPose(Axis.YP.rotationDegrees(180.0F - p_115553_));
        PaintingVariant paintingvariant = p_115552_.getVariant().value();
        float f = 0.0625F;
        p_115555_.scale(0.0625F, 0.0625F, 0.0625F);
        VertexConsumer vertexconsumer = p_115556_.getBuffer(RenderType.entitySolid(this.getTextureLocation(p_115552_)));
        PaintingTextureManager paintingtexturemanager = Minecraft.getInstance().getPaintingTextures();
        this.renderPainting(
            p_115555_,
            vertexconsumer,
            p_115552_,
            paintingvariant.getWidth(),
            paintingvariant.getHeight(),
            paintingtexturemanager.get(paintingvariant),
            paintingtexturemanager.getBackSprite()
        );
        p_115555_.popPose();
        super.render(p_115552_, p_115553_, p_115554_, p_115555_, p_115556_, p_115557_);
    }

    public ResourceLocation getTextureLocation(Painting p_115550_) {
        return Minecraft.getInstance().getPaintingTextures().getBackSprite().atlasLocation();
    }

    private void renderPainting(
        PoseStack p_115559_,
        VertexConsumer p_115560_,
        Painting p_115561_,
        int p_115562_,
        int p_115563_,
        TextureAtlasSprite p_115564_,
        TextureAtlasSprite p_115565_
    ) {
        PoseStack.Pose posestack$pose = p_115559_.last();
        float f = (float)(-p_115562_) / 2.0F;
        float f1 = (float)(-p_115563_) / 2.0F;
        float f2 = 0.5F;
        float f3 = p_115565_.getU0();
        float f4 = p_115565_.getU1();
        float f5 = p_115565_.getV0();
        float f6 = p_115565_.getV1();
        float f7 = p_115565_.getU0();
        float f8 = p_115565_.getU1();
        float f9 = p_115565_.getV0();
        float f10 = p_115565_.getV(0.0625F);
        float f11 = p_115565_.getU0();
        float f12 = p_115565_.getU(0.0625F);
        float f13 = p_115565_.getV0();
        float f14 = p_115565_.getV1();
        int i = p_115562_ / 16;
        int j = p_115563_ / 16;
        double d0 = 1.0 / (double)i;
        double d1 = 1.0 / (double)j;

        for (int k = 0; k < i; k++) {
            for (int l = 0; l < j; l++) {
                float f15 = f + (float)((k + 1) * 16);
                float f16 = f + (float)(k * 16);
                float f17 = f1 + (float)((l + 1) * 16);
                float f18 = f1 + (float)(l * 16);
                int i1 = p_115561_.getBlockX();
                int j1 = Mth.floor(p_115561_.getY() + (double)((f17 + f18) / 2.0F / 16.0F));
                int k1 = p_115561_.getBlockZ();
                Direction direction = p_115561_.getDirection();
                if (direction == Direction.NORTH) {
                    i1 = Mth.floor(p_115561_.getX() + (double)((f15 + f16) / 2.0F / 16.0F));
                }

                if (direction == Direction.WEST) {
                    k1 = Mth.floor(p_115561_.getZ() - (double)((f15 + f16) / 2.0F / 16.0F));
                }

                if (direction == Direction.SOUTH) {
                    i1 = Mth.floor(p_115561_.getX() - (double)((f15 + f16) / 2.0F / 16.0F));
                }

                if (direction == Direction.EAST) {
                    k1 = Mth.floor(p_115561_.getZ() + (double)((f15 + f16) / 2.0F / 16.0F));
                }

                int l1 = LevelRenderer.getLightColor(p_115561_.level(), new BlockPos(i1, j1, k1));
                float f19 = p_115564_.getU((float)(d0 * (double)(i - k)));
                float f20 = p_115564_.getU((float)(d0 * (double)(i - (k + 1))));
                float f21 = p_115564_.getV((float)(d1 * (double)(j - l)));
                float f22 = p_115564_.getV((float)(d1 * (double)(j - (l + 1))));
                this.vertex(posestack$pose, p_115560_, f15, f18, f20, f21, -0.5F, 0, 0, -1, l1);
                this.vertex(posestack$pose, p_115560_, f16, f18, f19, f21, -0.5F, 0, 0, -1, l1);
                this.vertex(posestack$pose, p_115560_, f16, f17, f19, f22, -0.5F, 0, 0, -1, l1);
                this.vertex(posestack$pose, p_115560_, f15, f17, f20, f22, -0.5F, 0, 0, -1, l1);
                this.vertex(posestack$pose, p_115560_, f15, f17, f4, f5, 0.5F, 0, 0, 1, l1);
                this.vertex(posestack$pose, p_115560_, f16, f17, f3, f5, 0.5F, 0, 0, 1, l1);
                this.vertex(posestack$pose, p_115560_, f16, f18, f3, f6, 0.5F, 0, 0, 1, l1);
                this.vertex(posestack$pose, p_115560_, f15, f18, f4, f6, 0.5F, 0, 0, 1, l1);
                this.vertex(posestack$pose, p_115560_, f15, f17, f7, f9, -0.5F, 0, 1, 0, l1);
                this.vertex(posestack$pose, p_115560_, f16, f17, f8, f9, -0.5F, 0, 1, 0, l1);
                this.vertex(posestack$pose, p_115560_, f16, f17, f8, f10, 0.5F, 0, 1, 0, l1);
                this.vertex(posestack$pose, p_115560_, f15, f17, f7, f10, 0.5F, 0, 1, 0, l1);
                this.vertex(posestack$pose, p_115560_, f15, f18, f7, f9, 0.5F, 0, -1, 0, l1);
                this.vertex(posestack$pose, p_115560_, f16, f18, f8, f9, 0.5F, 0, -1, 0, l1);
                this.vertex(posestack$pose, p_115560_, f16, f18, f8, f10, -0.5F, 0, -1, 0, l1);
                this.vertex(posestack$pose, p_115560_, f15, f18, f7, f10, -0.5F, 0, -1, 0, l1);
                this.vertex(posestack$pose, p_115560_, f15, f17, f12, f13, 0.5F, -1, 0, 0, l1);
                this.vertex(posestack$pose, p_115560_, f15, f18, f12, f14, 0.5F, -1, 0, 0, l1);
                this.vertex(posestack$pose, p_115560_, f15, f18, f11, f14, -0.5F, -1, 0, 0, l1);
                this.vertex(posestack$pose, p_115560_, f15, f17, f11, f13, -0.5F, -1, 0, 0, l1);
                this.vertex(posestack$pose, p_115560_, f16, f17, f12, f13, -0.5F, 1, 0, 0, l1);
                this.vertex(posestack$pose, p_115560_, f16, f18, f12, f14, -0.5F, 1, 0, 0, l1);
                this.vertex(posestack$pose, p_115560_, f16, f18, f11, f14, 0.5F, 1, 0, 0, l1);
                this.vertex(posestack$pose, p_115560_, f16, f17, f11, f13, 0.5F, 1, 0, 0, l1);
            }
        }
    }

    private void vertex(
        PoseStack.Pose p_323991_,
        VertexConsumer p_254114_,
        float p_254164_,
        float p_254459_,
        float p_254183_,
        float p_253615_,
        float p_254448_,
        int p_253660_,
        int p_254342_,
        int p_253757_,
        int p_254101_
    ) {
        p_254114_.vertex(p_323991_, p_254164_, p_254459_, p_254448_)
            .color(255, 255, 255, 255)
            .uv(p_254183_, p_253615_)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(p_254101_)
            .normal(p_323991_, (float)p_253660_, (float)p_254342_, (float)p_253757_)
            .endVertex();
    }
}
