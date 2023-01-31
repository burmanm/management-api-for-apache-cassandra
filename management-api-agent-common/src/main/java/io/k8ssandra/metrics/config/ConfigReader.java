package io.k8ssandra.metrics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.k8ssandra.metrics.builder.relabel.RelabelSpec;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class ConfigReader {
    public static final String CONFIG_PATH_PROPERTY = "collector-config-path";
    public static final String CONFIG_PATH_DEFAULT = "/configs/metrics-collector.yaml";

    public static Configuration readConfig() {
        // Read first the default settings from the agent, which are prepended to the Configuration relabels and labels
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource("default-metric-settings.yaml");
        String defaultSettings = resource.getFile();

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        // Default Configuration
        try {
            Configuration defaultConfig = mapper.readValue(new File(defaultSettings), Configuration.class);
            Configuration customConfig = readCustomConfig();

            if(customConfig != null) {
                // Prepend default relabeling rules
                List<RelabelSpec> customRelabels = customConfig.getRelabels();
                List<RelabelSpec> defaultRelabels = defaultConfig.getRelabels();
                if(customRelabels != null) {
                    defaultRelabels.addAll(customRelabels);
                }

                customConfig.setRelabels(defaultRelabels);

                // Set default env variables
                LabelConfiguration labels = defaultConfig.getLabels();
                if(customConfig.getLabels() != null && customConfig.getLabels().getEnvVariables() != null) {
                    labels.getEnvVariables().putAll(customConfig.getLabels().getEnvVariables());
                }
                customConfig.setLabels(labels);

                return customConfig;
            }

            return defaultConfig;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Configuration readCustomConfig() {
        // Check env variable if there's any changes to the config path
        String configPath = System.getProperty(CONFIG_PATH_PROPERTY);
        if(configPath == null) {
            String maacPath = System.getenv("MAAC_PATH");
            configPath = String.format("%s%s", maacPath, CONFIG_PATH_DEFAULT);
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        File configFile = new File(configPath);

        // FileNotFoundException should be thrown if override is used
        if (configFile.exists() || !configPath.endsWith(CONFIG_PATH_DEFAULT)) {
            try {
                return mapper.readValue(configFile, Configuration.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return new Configuration();
    }
}
