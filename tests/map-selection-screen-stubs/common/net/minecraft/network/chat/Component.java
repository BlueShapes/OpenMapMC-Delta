package net.minecraft.network.chat;

public final class Component {
    private final String value;

    private Component(String value) { this.value = value; }

    public static Component translatable(String key, Object... args) {
        String value = switch (key) {
            case "screen.odysseymap.map_mod_selection" -> "Choose a map mod";
            case "gui.odysseymap.map_mod_selection.description" -> "Your installed map mods have changed. Choose one to enable. You can choose again in OpenMap Delta settings.";
            case "gui.odysseymap.map_mod_selection.instructions" -> "Choose one map mod to enable. Installed mods and map data are kept.";
            case "gui.odysseymap.map_mod_selection.choose" -> "Enable " + args[0];
            case "gui.odysseymap.map_mod_selection.keep" -> "Keep current settings";
            case "gui.odysseymap.map_mod_selection.continue_unsaved" -> "Continue without saving";
            case "gui.odysseymap.map_mod_selection.failed" -> "Could not save the selection. Check the log and try again.";
            default -> key;
        };
        return new Component(value);
    }

    public static Component literal(String value) { return new Component(value); }
    public static Component empty() { return new Component(""); }
    public Component copy() { return new Component(value); }
    public Component append(String suffix) { return new Component(value + suffix); }
    public Component append(Component suffix) { return new Component(value + suffix.getString()); }

    public String getString() { return value; }
    @Override public String toString() { return value; }
}
