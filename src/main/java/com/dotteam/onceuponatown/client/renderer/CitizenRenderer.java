package com.dotteam.onceuponatown.client.renderer;

import com.dotteam.onceuponatown.client.model.CitizenModel;
import com.dotteam.onceuponatown.client.model.layer.CitizenExtraLayer;
import com.dotteam.onceuponatown.entity.Citizen;
import com.dotteam.onceuponatown.util.OuatUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CitizenRenderer extends HumanoidMobRenderer<Citizen, CitizenModel<Citizen>> {
    private static final ResourceLocation CITIZEN_BASE_SKIN = OuatUtils.resource("textures/entity/citizen/base_skin.png");

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new CitizenModel<>(context.bakeLayer(CitizenModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new CitizenExtraLayer<>(this));
        // TODO : make an armor that fit well the citizen body, especially the head
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
        //this.addLayer(new CrossedArmsItemLayer<>(this, pContext.getItemInHandRenderer()));
    }

    public void render(Citizen citizen, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
        getModel().setCrossedArms(citizen.isCrossingArms());
        super.render(citizen, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    protected void scale(Citizen citizen, PoseStack matrixStack, float partialTickTime) {
        float f = 0.9375F;
        if (citizen.isBaby()) {
            f *= 0.5F;
            this.shadowRadius = 0.25F;
        } else {
            this.shadowRadius = 0.5F;
        }
        matrixStack.scale(f, f, f);
    }

    public ResourceLocation getTextureLocation(Citizen citizen) {return CITIZEN_BASE_SKIN;}
}
