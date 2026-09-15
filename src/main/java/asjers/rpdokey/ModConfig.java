package asjers.rpdokey;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record ModConfig(boolean lockAllByDefault) {
	public static final Logger LOGGER = LoggerFactory.getLogger("rpdokey");

	private static final Path CONFIG_PATH = FabricLoader
		.getInstance()
		.getConfigDir()
		.resolve("rpdokey.properties");

	public static ModConfig load() {
		Properties properties = new Properties();
		boolean lockAll = false;

		if (Files.exists(CONFIG_PATH)) {
			try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
				properties.load(input);
				lockAll = Boolean.parseBoolean(properties.getProperty("lock-all-by-default", "false"));
				LOGGER.info("Loaded config: lock-all-by-default = {}", lockAll);
			} catch (Exception e) {
				LOGGER.error("Failed to read config, using default false", e);
			}
		} else {
			saveDefaults(properties);
		}

		return new ModConfig(lockAll);
	}

	private static void saveDefaults(Properties properties) {
		properties.setProperty("lock-all-by-default", "false");
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
				properties.store(
					output,
					"RPDoKey Configuration\n"
					+ "lock-all-by-default:\n"
					+ "  false = Only doors specifically assigned with `/rpdokey lock` are locked\n"
					+ "  true = Every door on the server is locked unless an OP unlocks it"
				);
				LOGGER.info("Created default config file at {}", CONFIG_PATH);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to create default config", e);
		}
	}
}