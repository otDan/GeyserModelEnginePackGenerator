package re.imc.geysermodelenginepackgenerator.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zimzaza4.geyserutils.geyser.GeyserUtils;
import re.imc.geysermodelenginepackgenerator.ExtensionMain;
import re.imc.geysermodelenginepackgenerator.util.temp.ModelVariant;

import java.util.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Entity {
    public static final String TEMPLATE = """
            {
              "format_version": "1.10.0",
              "minecraft:client_entity": {
                "description": {
                  "identifier": "modelengine:%entity_id%",
                  "materials": {
                    "default": "%material%",
                    "anim": "entity_alphatest_anim_change_color_one_sided"
                  },
                  "textures": {
                  },
                  "geometry": {
                  
                  },
                  "animations": {
                    "look_at_target": "%look_at_target%"
                  },
                  "scripts": {
                    "animate": [
                      "look_at_target"
                    ]
                  },
                  "render_controllers": [
                  ]
                }
              }
            }
            """;


    String modelId;
    JsonObject json;
    boolean hasHeadAnimation = false;
    Animation animation;
    Geometry geometry;
    RenderController renderController;
    String path;
    Map<String, Texture> textureMap = new HashMap<>();
    ModelConfig modelConfig;





    public Entity(String modelId) {
        this.modelId = modelId;
    }

    public void modify(Map<String, List<Texture>> allVariants) {

        json = new JsonParser().parse(TEMPLATE.replace("%entity_id%", modelId)
                .replace("%geometry%", "geometry.meg_" + modelId)
                .replace("%texture%", "textures/entity/" + path + modelId)
                .replace("%look_at_target%",  modelConfig.isEnableHeadRotation() ? "animation." + modelId + ".look_at_target" : "animation.none")
                .replace("%material%", modelConfig.getMaterial())).getAsJsonObject();

        JsonObject description = json.get("minecraft:client_entity").getAsJsonObject().get("description").getAsJsonObject();
        JsonObject jsonTextures = description.get("textures").getAsJsonObject();
        JsonObject jsonGeometry = description.get("geometry").getAsJsonObject();
        JsonObject jsonMaterials = description.get("materials").getAsJsonObject();

        JsonArray jsonRenderControllers = description.get("render_controllers").getAsJsonArray();

        Map<String, String> materials = getModelConfig().getTextureMaterials();
        materials.forEach(jsonMaterials::addProperty);

        if (modelConfig.getPerTextureUvSize().isEmpty()) {
            jsonGeometry.addProperty("default", "geometry.meg_" + modelId);
            jsonTextures.addProperty("default", "textures/entity/" + path + modelId + "/" + textureMap.keySet().stream().findFirst().orElse("def"));
        }

        for (String name : textureMap.keySet()) {
            if (modelConfig.getPerTextureUvSize().containsKey(name)) {
                Integer[] size = modelConfig.getPerTextureUvSize().getOrDefault(name, new Integer[]{16, 16});
                String suffix = size[0] + "_" + size[1];

                jsonGeometry.addProperty("t_" + suffix, "geometry.meg_" + modelId + "_" + suffix);
                jsonTextures.addProperty(name, "textures/entity/" + path + modelId + "/" + name);

            }
            jsonRenderControllers.add("controller.render." + modelId + "_" + name);

        }

        JsonObject textures = description.getAsJsonObject("textures");
        var textureVariants = allVariants.get(modelId);
        if (textureVariants != null) {
            for (var modelVariant : ModelVariant.values()) {
                boolean found = false;

                for (var variant : textureVariants) {
                    String variantName = variant.getOriginalPath().toFile().getName().replace(".png", "");
                    if (modelVariant.name().equals(variantName.toUpperCase())) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    textures.addProperty(modelVariant.name().toLowerCase(), "textures/entity/" + path + modelId + "/variant/" + modelVariant.name().toLowerCase());
                    ExtensionMain.LOGGER.warning("Added texture variant %s for %s".formatted(modelVariant.name().toLowerCase(), modelId));
                }
                else {
                    textures.addProperty(modelVariant.name().toLowerCase(), "textures/entity/" + path + modelId + "/" + modelId);
                    ExtensionMain.LOGGER.warning("Added not found texture variant %s for %s".formatted(modelVariant.name().toLowerCase(), modelId));
                }
            }
        }

        JsonObject jsonAnimations = description.getAsJsonObject("animations");
        JsonArray animate = description.getAsJsonObject("scripts").getAsJsonArray("animate");

        if (animation != null) {
            for (String animation : animation.animationIds) {
                String controller = "controller.animation." + modelId + "." + animation;
                animate.add(animation + "_control");
                jsonAnimations.addProperty(animation, "animation." + modelId + "." + animation);
                jsonAnimations.addProperty(animation + "_control", controller);
            }
        }
    }

    public void register() {

        String id = "modelengine:" + modelId;
        GeyserUtils.addCustomEntity(id);
        if (geometry == null) {
            return;
        }
        if (!modelConfig.isDisablePartVisibility()) {
            for (int i = 0; i < Math.ceil(geometry.getBones().size() / 24f); i++) {
                GeyserUtils.addProperty(id, "modelengine:bone" + i, Integer.class);
            }
        }

        if (animation != null) {
            for (int i = 0; i < Math.ceil(animation.animationIds.size() / 24f); i++) {
                GeyserUtils.addProperty(id, "modelengine:anim" + i, Integer.class);
            }
        }
        GeyserUtils.registerProperties(id);
    }
}
