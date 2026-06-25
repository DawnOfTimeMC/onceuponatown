package org.dawnoftime.onceuponatown.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.List;

public class NpcModel<T extends Npc> extends HumanoidModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(Ouat.MOD_ID, "npc"), "main_layer");
    private final List<ModelPart> parts;
    private final ModelPart crossedArms;

    public NpcModel(ModelPart root) {
        super(root);
        this.crossedArms = this.body.getChild("crossed_arms");
        this.parts = root.getAllParts().filter((part) -> !part.isEmpty()).collect(ImmutableList.toImmutableList());
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
        PartDefinition root = meshDefinition.getRoot();
        // Hat
        PartDefinition hat = root.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.51F)), PartPose.ZERO);
        hat.addOrReplaceChild("hat_rim", CubeListBuilder.create().texOffs(30, 47).addBox(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F), PartPose.rotation((-(float) Math.PI / 2F), 0.0F, 0.0F));
        // Head
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F), PartPose.ZERO);
        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, -2.0F, 0.0F));
        // Body and crossed arms
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 20).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F), PartPose.ZERO);
        body.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(0, 38).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.ZERO);
        body.addOrReplaceChild("crossed_arms", CubeListBuilder.create().texOffs(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(44, 22).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(44, 22).mirror().addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 3.0F, -1.0F, -0.75F, 0.0F, 0.0F));
        // Arms -- pivot at shoulder position matching vanilla HumanoidModel convention.
        // (0,0,0) placed the pivot at the model center, hiding the arm inside the body mesh
        // and making all rotation animations invisible. Correct positions: (-5,2,0) / (5,2,0).
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(44, 22).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(5.0F, 2.0F, 0.0F));
        // Legs
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(-2.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F), PartPose.offset(2.0F, 12.0F, 0.0F));
        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    @Override
    public void prepareMobModel(T npc, float limbSwing, float limbSwingAmount, float partialTick) {
        HumanoidModel.ArmPose leftArmPose = getArmPose(npc, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose rightArmPose = getArmPose(npc, InteractionHand.OFF_HAND);
        if (leftArmPose.isTwoHanded())
            rightArmPose = npc.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (npc.getMainArm() == HumanoidArm.RIGHT) {
            this.rightArmPose = leftArmPose;
            this.leftArmPose = rightArmPose;
        } else {
            this.rightArmPose = rightArmPose;
            this.leftArmPose = leftArmPose;
        }
        setCrossedArms(npc.isCrossingArms());
        super.prepareMobModel(npc, limbSwing, limbSwingAmount, partialTick);
    }

    @Override
    public void setupAnim(T npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.head.zRot = 0.0F;
        this.hat.xRot = this.head.xRot;

        // Detect a new block placement: generation counter changed since last frame.
        int gen = npc.getBuildGeneration();
        if (gen != npc.clientLastBuildGeneration) {
            npc.clientLastBuildGeneration = gen;
            npc.clientBuildPlacedAtAge = ageInTicks;
        }

        float elapsed = ageInTicks - npc.clientBuildPlacedAtAge;
        float duration = 7.0f; // ticks for the swing animation to complete
        if (elapsed >= 0 && elapsed <= duration) {
            // t = 1.0 at placement, 0.0 at end of animation.
            float t = 1.0f - (elapsed / duration);
            // Ease-out quadratic: fast start, decelerates to rest.
            float swing = t * (2.0f - t);
            // Lateral arc: peaks mid-animation to give the arm a natural curved path.
            float arc = Mth.sin((float) Math.PI * (1.0f - t));
            this.rightArm.xRot -= swing * 1.2F;
            this.rightArm.zRot -= arc * 0.18F;
        }

        animateReadingPose(npc);
    }

    private void animateReadingPose(T npc) {
        if (npc.isReading()) {
            if (!npc.isLeftHanded()) {
                this.rightArm.xRot = -1.65F;
                this.rightArm.yRot = -0.36F;
                this.rightArm.zRot = 1.5F;
                this.rightArm.y += 1F;
                this.rightArm.x -= 0.75F;
                this.leftArm.xRot = -1.2F;
                this.leftArm.yRot = 0.1F;
                this.leftArm.zRot = 0.1F;
            } else {
                this.leftArm.xRot = -1.65F;
                this.leftArm.yRot = 0.36F;
                this.leftArm.zRot = -1.5F;
                this.leftArm.y += 1F;
                this.leftArm.x += 0.75F;
                this.rightArm.xRot = -1.2F;
                this.rightArm.yRot = -0.1F;
                this.rightArm.zRot = -0.1F;
            }
            this.head.xRot = 0.38F;
            this.hat.xRot = this.head.xRot;
        }
    }

    private HumanoidModel.ArmPose getArmPose(T npc, InteractionHand hand) {
        ItemStack itemstack = npc.getItemInHand(hand);
        if (itemstack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (npc.getUsedItemHand() == hand && npc.getUseItemRemainingTicks() > 0) {
            UseAnim useanim = itemstack.getUseAnimation();
            if (useanim == UseAnim.BLOCK) return HumanoidModel.ArmPose.BLOCK;
            if (useanim == UseAnim.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
            if (useanim == UseAnim.SPEAR) return HumanoidModel.ArmPose.THROW_SPEAR;
            if (useanim == UseAnim.CROSSBOW && hand == npc.getUsedItemHand()) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            if (useanim == UseAnim.SPYGLASS) return HumanoidModel.ArmPose.SPYGLASS;
            if (useanim == UseAnim.TOOT_HORN) return HumanoidModel.ArmPose.TOOT_HORN;
            if (useanim == UseAnim.BRUSH) return HumanoidModel.ArmPose.BRUSH;
        } else if (!npc.swinging && itemstack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(itemstack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    public void setCrossedArms(boolean crossedArms) {
        this.crossedArms.visible = crossedArms;
        this.rightArm.visible = !crossedArms;
        this.leftArm.visible = !crossedArms;
    }

    public ModelPart getRandomModelPart(RandomSource random) {
        return this.parts.get(random.nextInt(this.parts.size()));
    }
}
