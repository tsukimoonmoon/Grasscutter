package emu.grasscutter.commands;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.GenshinPlayer;
import org.reflections.Reflections;

import java.util.*;

@SuppressWarnings("UnusedReturnValue")
public final class CommandMap {
    public static CommandMap getInstance() {
        return Grasscutter.getGameServer().getCommandMap();
    }
    
    private final Map<String, CommandHandler> commands = new HashMap<>();
    private final Map<String, Command> annotations = new HashMap<>();

    /**
     * Register a command handler.
     * @param label The command label.
     * @param command The command handler.
     * @return Instance chaining.
     */
    public CommandMap registerCommand(String label, CommandHandler command) {
        Grasscutter.getLogger().debug("Registered command: " + label);
        
        // Get command data.
        Command annotation = command.getClass().getAnnotation(Command.class);
        this.annotations.put(label, annotation);
        this.commands.put(label, command);
        
        // Register aliases.
        if(annotation.aliases().length > 0) {
            for (String alias : annotation.aliases()) {
                this.commands.put(alias, command);
                this.annotations.put(alias, annotation);
            }
        } return this;
    }

    /**
     * Removes a registered command handler.
     * @param label The command label.
     * @return Instance chaining.
     */
    public CommandMap unregisterCommand(String label) {
        Grasscutter.getLogger().debug("Unregistered command: " + label);
        CommandHandler handler = this.commands.get(label);
        if(handler == null) return this;
        
        Command annotation = handler.getClass().getAnnotation(Command.class);
        this.annotations.remove(label);
        this.commands.remove(label);
        
        // Unregister aliases.
        if(annotation.aliases().length > 0) {
            for (String alias : annotation.aliases()) {
                this.commands.remove(alias);
                this.annotations.remove(alias);
            }
        }
        
        return this;
    }

    /**
     * Returns a list of all registered commands.
     * @return All command handlers as a list.
     */
    public List<CommandHandler> getHandlersAsList() {
        return new LinkedList<>(this.commands.values());
    }

    public HashMap<String, CommandHandler> getHandlers() { return new LinkedHashMap<>(this.commands); }

    /**
     * Returns a handler by label/alias.
     * @param label The command label.
     * @return The command handler.
     */
    public CommandHandler getHandler(String label) {
        return this.commands.get(label);
    }

    /**
     * Invoke a command handler with the given arguments.
     * @param player The player invoking the command or null for the server console.
     * @param rawMessage The messaged used to invoke the command.
     */
    public void invoke(GenshinPlayer player, String rawMessage) {
        rawMessage = rawMessage.trim();
        if(rawMessage.length() == 0) {
            CommandHandler.sendMessage(player, "No command specified."); return;
        }
        
        // Remove prefix if present.
        if(!Character.isLetter(rawMessage.charAt(0)))
            rawMessage = rawMessage.substring(1);
        
        // Parse message.
        String[] split = rawMessage.split(" ");
        List<String> args = new LinkedList<>(Arrays.asList(split));
        String label = args.remove(0);
        
        // Get command handler.
        CommandHandler handler = this.commands.get(label);
        if(handler == null) {
            CommandHandler.sendMessage(player, "Unknown command: " + label); return;
        }
        
        // Check for permission.
        if(player != null) {
            String permissionNode = this.annotations.get(label).permission();
            Account account = player.getAccount();
            if(!permissionNode.isEmpty() && !account.hasPermission(permissionNode)) {
                CommandHandler.sendMessage(player, "You do not have permission to run this command."); return;
            }
        }
        
        // Execution power check.
        Command.Execution executionPower = this.annotations.get(label).execution();
        if(player == null && executionPower == Command.Execution.PLAYER) {
            CommandHandler.sendMessage(null, "Run this command in-game."); return;
        } else if (player != null && executionPower == Command.Execution.CONSOLE) {
            CommandHandler.sendMessage(player, "This command can only be run from the console."); return;
        }
        
        // Invoke execute method for handler.
        if(player == null) handler.execute(args); 
        else handler.execute(player, args);
    }
    
    public CommandMap() {
        this(false);
    }
    
    public CommandMap(boolean scan) {
        if(scan) this.scan();
    }

    /**
     * Scans for all classes annotated with {@link Command} and registers them.
     */
    private void scan() {
        Reflections reflector = Grasscutter.reflector;
        Set<Class<?>> classes = reflector.getTypesAnnotatedWith(Command.class);
        classes.forEach(annotated -> {
            try {
                Command cmdData = annotated.getAnnotation(Command.class);
                Object object = annotated.newInstance();
                if (object instanceof CommandHandler)
                    this.registerCommand(cmdData.label(), (CommandHandler) object);
                else Grasscutter.getLogger().error("Class " + annotated.getName() + " is not a CommandHandler!");
            } catch (Exception exception) {
                Grasscutter.getLogger().error("Failed to register command handler for " + annotated.getSimpleName(), exception);
            }
        });
    }
}
