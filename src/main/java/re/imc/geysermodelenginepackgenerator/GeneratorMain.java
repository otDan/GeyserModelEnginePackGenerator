package re.imc.geysermodelenginepackgenerator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import re.imc.geysermodelenginepackgenerator.generator.*;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class GeneratorMain {
    public static final Map<String, Entity> entityMap = new HashMap<>();
    public static final Map<String, Animation> animationMap = new HashMap<>();
    public static final Map<String, Geometry> geometryMap = new HashMap<>();
    public static final Map<String, Map<String, Texture>> textureMap = new HashMap<>();
    public static final Map<String, List<Texture>> variantTextureMap = new HashMap<>();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .create();


    public static void main(String[] args) {
        File source = new File(args.length > 0 ? args[0] : "input");

        File output = new File("output");

        startGenerate(source, output);
    }

    public static void generateFromFolder(String currentPath, File folder) {
        if (folder.listFiles() == null) {
            return;
        }
        String modelId = folder.getName().toLowerCase();

        Entity entity = new Entity(modelId);
        ModelConfig modelConfig = new ModelConfig();
        boolean shouldOverrideConfig = false;
        File textureConfigFile = new File(folder, "config.json");
        if (textureConfigFile.exists()) {
            try {
                modelConfig = GSON.fromJson(Files.readString(textureConfigFile.toPath()), ModelConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        boolean canAdd = false;
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                if (file.getName().contains("variant")) {
                    for (File file2 : file.listFiles()) {
                        if (file2.getName().endsWith(".png")) {
                            ExtensionMain.LOGGER.warning("File %s parent is %s".formatted(file2.toPath(), file2.getParent()));
                            if (!variantTextureMap.containsKey(modelId))
                                variantTextureMap.put(modelId, new ArrayList<>());

                            String textureName = file.getName().replace(".png", "");
                            Set<String> bindingBones = new HashSet<>();
                            bindingBones.add("*");
                            if (modelConfig.getBingingBones().containsKey(textureName)) {
                                bindingBones = modelConfig.getBingingBones().get(textureName);
                            }

                            variantTextureMap.get(modelId).add(new Texture(modelId, currentPath, bindingBones, file2.toPath()));
                            ExtensionMain.LOGGER.warning("Added texture to %s at path %s and file %s".formatted(modelId, file.getPath(), file2.toPath()));
                            ExtensionMain.LOGGER.warning("Current size for %s is %s".formatted(modelId, variantTextureMap.get(modelId).size()));
                        }
                        else {
                            ExtensionMain.LOGGER.warning("Found unknown variant %s in %s".formatted(file2.getPath(), currentPath));
                        }
                    }
                }
                else
                    generateFromFolder(currentPath + folder.getName() + "/", file);
            }
            if (file.getName().endsWith(".png")) {
                String textureName = file.getName().replace(".png", "");
                Set<String> bindingBones = new HashSet<>();
                bindingBones.add("*");
                if (modelConfig.getBingingBones().containsKey(textureName)) {
                    bindingBones = modelConfig.getBingingBones().get(textureName);
                }
                Map<String, Texture> map = textureMap.computeIfAbsent(modelId, s -> new HashMap<>());
                map.put(textureName, new Texture(modelId, currentPath, bindingBones, file.toPath()));
                entity.setTextureMap(map);
                if (modelConfig.getBingingBones().isEmpty()) {
                    modelConfig.getBingingBones().put(textureName, Set.of("*"));
                    shouldOverrideConfig = true;
                }

            }
            if (file.getName().endsWith(".json")) {
                try {
                    String json = Files.readString(file.toPath());
                    if (isAnimationFile(json)) {
                        Animation animation = new Animation();
                        animation.setPath(currentPath);
                        animation.setModelId(modelId);

                        animation.load(json);
                        animationMap.put(modelId, animation);
                        entity.setAnimation(animation);
                    }

                    if (isGeometryFile(json)) {
                        Geometry geometry = new Geometry();
                        geometry.load(json);
                        geometry.setPath(currentPath);
                        geometry.setModelId(modelId);
                        geometryMap.put(modelId, geometry);
                        entity.setGeometry(geometry);
                        canAdd = true;
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        if (canAdd) {
            // old config
            File oldConfig = new File(folder, "config.properties");
            Properties old = new Properties();
            try {
                if (oldConfig.exists()) {
                    old.load(new FileReader(oldConfig));
                    modelConfig.setMaterial(old.getProperty("material", "entity_alphatest_change_color"));
                    modelConfig.setEnableBlendTransition(Boolean.parseBoolean(old.getProperty("blend-transition", "true")));
                    modelConfig.setEnableHeadRotation(Boolean.parseBoolean(old.getProperty("head-rotation", "true")));
                    shouldOverrideConfig = true;
                    oldConfig.delete();
                }

            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if (shouldOverrideConfig) {
                try {
                    Files.writeString(textureConfigFile.toPath(), GSON.toJson(modelConfig));
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            entity.setModelConfig(modelConfig);
            entity.setPath(currentPath);
            entityMap.put(modelId, entity);
        }
    }

    public static void startGenerate(File source, File output) {
        ExtensionMain.LOGGER.warning("Starting new pack generation...");

        for (File file1 : source.listFiles()) {
            if (file1.isDirectory()) {
                if (file1.listFiles() == null) {
                    continue;
                }
                generateFromFolder("", file1);
            }
        }

        File animationsFolder = new File(output, "animations");
        File entityFolder = new File(output, "entity");
        File modelsFolder = new File(output, "models/entity");
        File texturesFolder = new File(output, "textures/entity");
        File animationControllersFolder = new File(output, "animation_controllers");
        File renderControllersFolder = new File(output, "render_controllers");
        File materialsFolder = new File(output, "materials");

        File manifestFile = new File(output, "manifest.json");


        output.mkdirs();
        if (!manifestFile.exists()) {
            try {
                Files.writeString(manifestFile.toPath(),
                        PackManifest.generate(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        animationsFolder.mkdirs();
        entityFolder.mkdirs();
        modelsFolder.mkdirs();
        texturesFolder.mkdirs();
        animationControllersFolder.mkdirs();
        renderControllersFolder.mkdirs();
        materialsFolder.mkdirs();

        File materialFile = new File(materialsFolder, "entity.material");

        if (!materialFile.exists()) {
            try {
                Files.writeString(materialFile.toPath(),
                        Material.TEMPLATE, StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        for (Map.Entry<String, Animation> entry : animationMap.entrySet()) {
            Entity entity = entityMap.get(entry.getKey());
            Geometry geo = geometryMap.get(entry.getKey());
            if (geo != null) {
                entry.getValue().addHeadBind(geo);
            }
            Path path = animationsFolder.toPath().resolve(entry.getValue().getPath() + entry.getKey() + ".animation.json");
            Path pathController = animationControllersFolder.toPath().resolve(entry.getValue().getPath() + entry.getKey() + ".animation_controllers.json");

            pathController.toFile().getParentFile().mkdirs();
            path.toFile().getParentFile().mkdirs();

            if (path.toFile().exists()) {
                continue;
            }

            AnimationController controller = new AnimationController();
            controller.load(entry.getValue(), entity);

            try {
                Files.writeString(path, GSON.toJson(entry.getValue().getJson()), StandardCharsets.UTF_8);
                Files.writeString(pathController, controller.getJson().toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (Map.Entry<String, Geometry> entry : geometryMap.entrySet()) {
            entry.getValue().modify();
            Path path = modelsFolder.toPath().resolve(entry.getValue().getPath() + entry.getKey() + ".geo.json");
            path.toFile().getParentFile().mkdirs();
            String id = entry.getValue().getGeometryId();

            Entity entity = entityMap.get(entry.getKey());
            if (entity != null) {
                ModelConfig modelConfig = entity.getModelConfig();
                if (!modelConfig.getPerTextureUvSize().isEmpty()) {
                    for (Map.Entry<String, Texture> textureEntry : entity.getTextureMap().entrySet()) {
                        String name = textureEntry.getKey();

                        Integer[] size = modelConfig.getPerTextureUvSize().getOrDefault(name, new Integer[]{16, 16});
                        String suffix = size[0] + "_" + size[1];
                        entry.getValue().setTextureWidth(size[0]);
                        entry.getValue().setTextureHeight(size[1]);
                        path = modelsFolder.toPath().resolve(entry.getValue().getPath() + entry.getKey() + "_" + suffix + ".geo.json");

                        entry.getValue().setId(id + "_" + suffix);

                        if (path.toFile().exists()) {
                            continue;
                        }

                        try {
                            Files.writeString(path, GSON.toJson(entry.getValue().getJson()), StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }

            if (path.toFile().exists()) {
                continue;
            }

            try {
                Files.writeString(path, GSON.toJson(entry.getValue().getJson()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (Map.Entry<String, Map<String, Texture>> textures : textureMap.entrySet()) {

            for (Map.Entry<String, Texture> entry : textures.getValue().entrySet()) {
                Path path = texturesFolder.toPath().resolve(entry.getValue().getPath() + textures.getKey() + "/" + entry.getKey() + ".png");
                path.toFile().getParentFile().mkdirs();

                if (path.toFile().exists()) {
                    continue;
                }
                try {
                    Files.copy(entry.getValue().getOriginalPath(), path, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        for (var entry : variantTextureMap.entrySet()) {
            ExtensionMain.LOGGER.warning("Found textures for %s are %s".formatted(entry.getKey(), entry.getValue().size()));
            for (var texture : entry.getValue()) {
                ExtensionMain.LOGGER.warning("Added texture variant at path %s and original path %s".formatted(texture.getPath(), texture.getOriginalPath()));
                Path path = texturesFolder.toPath().resolve(entry.getKey());
                Path variantFolder = path.resolve("variant");
                variantFolder.toFile().mkdirs();
                Path texturePath = variantFolder.resolve(texture.getOriginalPath().getFileName());
                texturePath.toFile().getParentFile().mkdirs();
                ExtensionMain.LOGGER.warning("Added texture variant at path %s".formatted(texturePath));

                if (texturePath.toFile().exists()) {
                    continue;
                }
                try {
                    Files.copy(texture.getOriginalPath(), texturePath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        for (Map.Entry<String, Entity> entry : entityMap.entrySet()) {
            Entity entity = entry.getValue();
            entity.modify(variantTextureMap);

            Path entityPath = entityFolder.toPath().resolve(entity.getPath() + entry.getKey() + ".entity.json");
            entityPath.toFile().getParentFile().mkdirs();
            if (entityPath.toFile().exists()) {
                continue;
            }
            try {
                Files.writeString(entityPath, entity.getJson().toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // render controller part

            String id = entity.getModelId();
            if (!geometryMap.containsKey(id)) continue;
            RenderController controller = new RenderController(id, geometryMap.get(id).getBones(), entity);
            entity.setRenderController(controller);
            Path renderPath = new File(renderControllersFolder, "controller.render." + id + ".json").toPath();
            if (renderPath.toFile().exists()) {
                continue;
            }
            try {
                Files.writeString(renderPath, controller.generate(variantTextureMap), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /*
        File controller = new File(animationControllersFolder, "modelengine.animation_controller.json");
        if (!controller.exists()) {
            try {
                Files.writeString(controller.toPath(), AnimationController.TEMPLATE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

         */
    }

    private static boolean isGeometryFile(String json) {
        try {
            return new JsonParser().parse(json).getAsJsonObject().has("minecraft:geometry");
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean isAnimationFile(String json) {
        try {
            return new JsonParser().parse(json).getAsJsonObject().has("animations");
        } catch (Throwable e) {
            return false;
        }
    }

}