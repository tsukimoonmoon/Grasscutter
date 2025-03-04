package emu.grasscutter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;

import emu.grasscutter.commands.CommandMap;
import emu.grasscutter.utils.Utils;
import org.reflections.Reflections;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import ch.qos.logback.classic.Logger;
import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.server.dispatch.DispatchServer;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.tools.Tools;
import emu.grasscutter.utils.Crypto;

public final class Grasscutter {
	private static final Logger log = (Logger) LoggerFactory.getLogger(Grasscutter.class);
	private static Config config;
	
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private static final File configFile = new File("./config.json");
	
	public static RunMode MODE = RunMode.BOTH;
	private static DispatchServer dispatchServer;
	private static GameServer gameServer;
	
	public static final Reflections reflector = new Reflections();
	
	static {
		// Declare logback configuration.
		System.setProperty("logback.configurationFile", "src/main/resources/logback.xml");
		
		// Load server configuration.
		Grasscutter.loadConfig();
		
		// Check server structure.
		Utils.startupCheck();
	}
	
    public static void main(String[] args) throws Exception {
    	Crypto.loadKeys();
    	
		for (String arg : args) {
			switch (arg.toLowerCase()) {
				case "-auth":
					MODE = RunMode.AUTH;
					break;
				case "-game":
					MODE = RunMode.GAME;
					break;
				case "-handbook":
					Tools.createGmHandbook();
					return;
			}
		}
		
		// Initialize server.
		Grasscutter.getLogger().info("Starting Grasscutter...");
		
		// Load all resources.
		ResourceLoader.loadAll();
		// Database
		DatabaseManager.initialize();
		
		// Start servers.
		dispatchServer = new DispatchServer();
		dispatchServer.start();
		
		gameServer = new GameServer(new InetSocketAddress(getConfig().GameServerIp, getConfig().GameServerPort));
		gameServer.start();
		
		// Open console.
		startConsole();
    }
	
	public static void loadConfig() {
		try (FileReader file = new FileReader(configFile)) {
			config = gson.fromJson(file, Config.class);
		} catch (Exception e) {
			Grasscutter.config = new Config(); saveConfig();
		}
	}
	
	public static void saveConfig() {
		try (FileWriter file = new FileWriter(configFile)) {
			file.write(gson.toJson(config));
		} catch (Exception e) {
			Grasscutter.getLogger().error("Config save error");
		}
	}
	
	public static void startConsole() {
		String input;
		try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
			while ((input = br.readLine()) != null) {
				try {
					CommandMap.getInstance().invoke(null, input);
				} catch (Exception e) {
					Grasscutter.getLogger().error("Command error: " + e.getMessage());
				}
			}
		} catch (Exception e) {
			Grasscutter.getLogger().error("An error occurred.", e);
		}
	}
	
	public enum RunMode {
		BOTH,
		AUTH,
		GAME
	}

	public static Config getConfig() {
		return config;
	}

	public static Logger getLogger() {
		return log;
	}

	public static Gson getGsonFactory() {
		return gson;
	}

	public static DispatchServer getDispatchServer() {
		return dispatchServer;
	}

	public static GameServer getGameServer() {
		return gameServer;
	}
}
