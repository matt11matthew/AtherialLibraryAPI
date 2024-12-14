package me.matthewedevelopment.atheriallib.config.yaml;

import me.matthewedevelopment.atheriallib.config.Config;
import me.matthewedevelopment.atheriallib.config.IgnoreValue;
import me.matthewedevelopment.atheriallib.config.SerializedName;
import me.matthewedevelopment.atheriallib.utilities.AtherialTasks;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public  class YamlConfig<A extends JavaPlugin> implements Config {
    private String path;
    private A plugin;
    private File file;

    public YamlConfiguration onLoad(YamlConfiguration yamlConfiguration) {
        return yamlConfiguration;
    }
    public YamlConfig(String path, A plugin) {
        this.path = path;
        this.plugin = plugin;

    }

    private boolean newConfig = false;
    private boolean done = false;

    protected boolean saveDefault = true;

    @Override
    public void loadConfig() {
        // Ensure the configuration file and its parent directories exist
        File configFile = this.getFile();
        if (!configFile.exists()) {
            if (saveDefault){
                this.saveDefaultConfig();
                newConfig=true;

            } else {
                //MAKE FILE
                if (!configFile.getParentFile().exists()){
                    configFile.getParentFile().mkdirs();
                }
                if (!configFile.exists()){
                    try {
                        configFile.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                newConfig=true;
            }
        }

        YamlConfiguration yamlConfiguration = onLoad(YamlConfiguration.loadConfiguration(configFile));

        Arrays.stream(getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(SerializedName.class))
                .filter(field -> !field.isAnnotationPresent(IgnoreValue.class))
                .forEach(field -> {
                    try {
                        field.setAccessible(true); // Access private fields

                        SerializedName annotation = field.getAnnotation(SerializedName.class);
                        String key = annotation.value();

                        // Check if the field type is registered in the CustomTypeRegistry

                        ConfigSerializable serializer = CustomTypeRegistry.getSerializer(field.getType());
                        if (serializer != null) {
                            if (!yamlConfiguration.isSet(key)) {
                                // Serialize and set default if not set in config
                                Object serialize = serializer.serialize(field.get(this));
                                if (serialize != null) {
                                    yamlConfiguration.set(key, serialize);
                                }
                            } else {

                                Object configSection = yamlConfiguration.get(annotation.value());
//                                System.err.println(serializer.getComplexity().toString() + ":" + configSection);
                                if (serializer.getComplexity() == SerializeType.COMPLEX) {
                                    Map<String, Object> map;
                                    if (configSection instanceof MemorySection) {
                                        MemorySection memorySection = (MemorySection) configSection;
                                        map = memorySection.getValues(false);
                                    } else if (configSection instanceof Map) {
                                        map = (Map<String, Object>) configSection;
                                    } else {
                                        System.err.println(configSection);
                                        throw new IllegalArgumentException("Unsupported configuration object type");

                                    }
                                    Object deserializedObject = serializer.deserializeComplex(map);
                                    field.set(this, deserializedObject);
                                } else {
                                    Object deserializedObject = serializer.deserializeSimple(configSection);
                                    field.set(this, deserializedObject);
                                }
                            }
                        } else {
                            if (Map.class.isAssignableFrom(field.getType())) {

                                Map<String, ?> maps = (Map) field.get(this);
                                Map<String, Object> newMap = new HashMap<>();
                                List<String> toIterate = new ArrayList<>();

//                                Bukkit.getServer().broadcastMessage(maps.toString());
                                if (!yamlConfiguration.isSet(key)){

                                    for (String s : maps.keySet()) {
//                                        Bukkit.getServer().broadcastMessage(s);

                                        toIterate.add(s);
                                    }
                                } else {
                                    for (String s : yamlConfiguration.getConfigurationSection(key).getKeys(false)) {
                                        toIterate.add(s);
//                                        Bukkit.getServer().broadcastMessage(ChatColor.GREEN+s);
                                    }
                                }
                                ConfigSerializable serializer2 = null;
                                for (String s : toIterate) {
                                    Object o = maps.get(s);
                                    if( o==null) {
                                        System.err.println(s +":" + o + " is null");
                                        continue;
                                    }
                                    if (serializer2==null){
                                        serializer2 =  CustomTypeRegistry.getSerializer(o.getClass());
                                    }
//                                    ConfigSerializable serializer2 = CustomTypeRegistry.getSerializer(o.getClass());
                                    if (serializer2 != null) {
                                        if (!yamlConfiguration.isSet(key + "." + s)) {
                                            // Serialize and set default if not set in config
                                            Object serialize = serializer2.serialize(o);
                                            if (serialize != null) {
                                                yamlConfiguration.set(key + "." + s, serialize);
                                            }
                                        } else {

                                            Object configSection = yamlConfiguration.get(annotation.value() + "." + s);
//                                            System.out.println(serializer2.getComplexity().toString() + ":" + configSection);
                                            if (serializer2.getComplexity() == SerializeType.COMPLEX) {
                                                Map<String, Object> map;
                                                if (configSection instanceof MemorySection) {
                                                    MemorySection memorySection = (MemorySection) configSection;
                                                    map = memorySection.getValues(false);
                                                } else if (configSection instanceof Map) {
                                                    map = (Map<String, Object>) configSection;
                                                } else {
                                                    throw new IllegalArgumentException("Unsupported configuration object type");
                                                }
                                                Object deserializedObject = serializer2.deserializeComplex(map);
                                                newMap.put(s, deserializedObject);
                                            } else {
                                                Object deserializedObject = serializer2.deserializeSimple(configSection);
                                                newMap.put(s, deserializedObject);
//                                                field.set(this, deserializedObject);
                                            }
                                        }
                                    }

                                    field.set(this, newMap);
                                }
                            } else {
                                if (!yamlConfiguration.isSet(key)) {

                                    yamlConfiguration.set(key, field.get(this));
                                } else {
                                    Object value = yamlConfiguration.get(key);
                                    field.set(this, field.getType().cast(value));
                                }
                            }
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
        //TODO SAVE
        try {
            yamlConfiguration.save(configFile);
        } catch (IOException e) {
            e.printStackTrace(); // Consider better error handling
        }
        if (newConfig&&!done){
            AtherialTasks.runIn(() -> {
                loadConfig();
            },10);
            done = true;
        }

    }

//    @Override
//    public void loadConfig() {
//        // Ensure the configuration file and its parent directories exist
//        File configFile = this.getFile();
//        if (!configFile.exists()) {
//            this.saveDefaultConfig();
//        }
//
//        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(configFile);
//
//        Arrays.stream(getClass().getDeclaredFields())
//                .filter(field -> field.isAnnotationPresent(SerializedName.class))
//                .filter(field -> !field.isAnnotationPresent(IgnoreValue.class))
//                .forEach(field -> {
//                    try {
//                        field.setAccessible(true); // Access private fields
//                        SerializedName annotation = field.getAnnotation(SerializedName.class);
//                        String key = annotation.value();
//                        Object fieldValue = field.get(this);
//
//                        if (ConfigSerializable.class.isAssignableFrom(field.getType())) {
//                            ConfigSerializable serializer = (ConfigSerializable) fieldValue;
//                            if (!yamlConfiguration.isSet(key)) {
//                                // Serialize and set default if not set in config
//                                yamlConfiguration.set(key, serializer.serialize(fieldValue));
//                            } else {
//                                // Deserialize and set field value
//                                Map<String, Object> map = (Map<String, Object>) yamlConfiguration.get(key);
//                                Object deserializedObject = serializer.deserialize(map);
//                                field.set(this, deserializedObject);
//                            }
//                        } else {
//                            // Handle standard types
//                            if (!yamlConfiguration.isSet(key)) {
//                                yamlConfiguration.set(key, fieldValue);
//                            } else {
//                                Object value = yamlConfiguration.get(key);
//                                field.set(this, field.getType().cast(value));
//                            }
//                        }
//                    } catch (IllegalAccessException e) {
//                        e.printStackTrace(); // Consider better error handling
//                    }
//                });
//
//        try {
//            yamlConfiguration.save(configFile);
//        } catch (IOException e) {
//            e.printStackTrace(); // Consider better error handling
//        }
//    }

//    @Override
//    public void loadConfig() {
//        // Ensure the configuration file and its parent directories exist
//        File configFile = this.getFile();
//        if (!configFile.exists()) {
//            this.saveDefaultConfig();
//        }
//
//        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(configFile);
//
//        Arrays.stream(getClass().getDeclaredFields())
//                .filter(field -> field.isAnnotationPresent(SerializedName.class))
//                .filter(field -> !field.isAnnotationPresent(IgnoreValue.class))
//                .forEach(field -> {
//                    try {
//                        SerializedName annotation = field.getAnnotation(SerializedName.class);
//                        Object fieldValue = field.get(this);
//
//                        if (fieldValue instanceof ConfigSerializable) {
//                            if (!yamlConfiguration.isSet(annotation.value())) {
//                                yamlConfiguration.set(annotation.value(), ((ConfigSerializable) fieldValue).serialize(fieldValue));
//                            } else {
//                                Map<String, Object> map = (Map<String, Object>) yamlConfiguration.get(annotation.value());
//                                ((ConfigSerializable) fieldValue).deserialize(map);
//                            }
//                        } else {
//                            if (!yamlConfiguration.isSet(annotation.value())) {
//                                yamlConfiguration.set(annotation.value(), fieldValue);
//                            } else {
//                                Object value = yamlConfiguration.get(annotation.value());
//                                field.set(this, field.getType().cast(value));
//                            }
//                        }
//                    } catch (IllegalAccessException e) {
//                        e.printStackTrace(); // Consider better error handling
//                    }
//                });
//
//        try {
//            yamlConfiguration.save(configFile);
//        } catch (IOException e) {
//            e.printStackTrace(); // Consider better error handling
//        }
//    }

    @Override
    public void reload() {
        this.loadConfig();
    }

    //        @Override
//        public void loadConfig() {
//            if (!this.plugin.getDataFolder().exists()) {
//                this.plugin.getDataFolder().mkdirs();
//            }
//            File file = this.getFile();
//            if (!this.exists()) {
//                File parentFile = file.getParentFile();
//                while (!parentFile.exists()) {
//                    parentFile.mkdirs();
//                    parentFile = parentFile.getParentFile();
//                }
//                if (!this.exists()) {
//                    this.saveDefaultConfig();
//                    file = this.getFile();
//                }
//            }
//            this.file = file;
//            YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(file);
//            File finalFile = file;
//
//            for (Map.Entry<String, Object> stringObjectEntry : defaultMap.entrySet()) {
//                if (!yamlConfiguration.isSet(stringObjectEntry.getKey())) {
//                    try {
//                        yamlConfiguration.set(stringObjectEntry.getKey(), stringObjectEntry.getValue());
//                        yamlConfiguration.save(finalFile);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//            Arrays.stream(getClass().getDeclaredFields())
//                    .filter(field -> field.isAnnotationPresent(SerializedName.class))
//                    .filter(field -> !field.isAnnotationPresent(IgnoreValue.class))
//                    .forEach(field -> {
//                        SerializedName annotation = field.getAnnotation(SerializedName.class);
//                        if (!yamlConfiguration.isSet(annotation.value())) {
//                            try {
//                                Object o = field.get(field.getType());
//                                if (o instanceof String || o instanceof Integer || o instanceof Double || o instanceof  Long || o instanceof Short || o instanceof Byte ) {
//                                    yamlConfiguration.set(annotation.value(), o);
//                                } else if (o instanceof List){
//                                    List<?> list = (List<?>) o;
//                                    System.err.println(list.getClass());
//                                }
//                                yamlConfiguration.save(finalFile);
//                            } catch (IllegalAccessException | IOException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                        try {
//                            field.set(field.getType(), field.getType().cast(yamlConfiguration.get(annotation.value())));
//                        } catch (IllegalAccessException e) {
//                            e.printStackTrace();
//                        }
//                    });
//
//        }
    @Override
    public void saveDefaultConfig() {
        if (!file.exists()) {
            this.plugin.saveResource(this.path, true);
        }
        this.file = getFile();
    }

    @Override
    public File getFile() {
        if (this.file == null) {
            this.file = new File(getPlugin().getDataFolder() + File.separator + getPath());
        }
        return file;
    }

    @Override
    public Plugin getPlugin() {
        return plugin;
    }

    @Override
    public String getPath() {
        return path;
    }
}

