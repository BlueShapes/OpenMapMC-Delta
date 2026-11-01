package net.aosankaku.openmapdelta.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import net.aosankaku.openmapdelta.OpenMapDeltaCommon;

/** Execute the real optional adapter with remapped and shifted fake bindings. */
public final class ControllerSupportHintsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(ControllerSupport.mapHints().isEmpty(), "absent controller integration is silent");
        inject("controllable", FakeControllable.class);
        inject("buttons", FakeButtons.class);
        inject("bindingRegistry", FakeRegistry.class);
        Minecraft.getInstance().screen=new FullscreenMapScreen();
        FakeRegistry.instance.put("pickup_item",90); FakeRegistry.instance.put("split_stack",91);
        FakeRegistry.instance.put("scroll_up",92); FakeRegistry.instance.put("scroll_down",93);
        FakeRegistry.instance.put("close_inventory",94);
        String legacy=hints();
        check(legacy.contains("test.button.90") && legacy.contains("test.button.94"),
                "older Controllable keeps generic GUI hints");
        for (var entry:Map.ofEntries(
                Map.entry("context",7),Map.entry("context_alternate",10),Map.entry("confirm",11),
                Map.entry("recenter",8),Map.entry("toggle_waypoints",3),Map.entry("close",6),
                Map.entry("zoom_in",0),Map.entry("zoom_in_alternate",4),
                Map.entry("zoom_out",1),Map.entry("zoom_out_alternate",5),
                Map.entry("pan_up",45),Map.entry("pan_down",46),Map.entry("pan_left",47),Map.entry("pan_right",48)
        ).entrySet()) FakeRegistry.instance.put("openmap."+entry.getKey(),entry.getValue());
        String profile=hints();
        for (int button:new int[]{7,10,11,8,3,6,0,4,1,5})
            check(java.util.regex.Pattern.compile("test\\.button\\."+button+"(?![0-9])").matcher(profile).find(),
                    "map hint reflects its active button "+button);
        check(!profile.contains("test.button.90") && !profile.contains("test.button.92"),
                "map profile does not advertise stale generic click and scroll controls");
        check(profile.contains("controller.pan") && profile.contains("controller.recenter")
                && profile.contains("controller.waypoints"), "new map actions have map-specific hints");
        FakeRegistry.instance.shift.put("controllable.key.openmap.context",17);
        FakeRegistry.instance.shift.put("controllable.key.openmap.confirm",18);
        FakeRegistry.instance.shift.put("controllable.key.openmap.context_alternate",-1);
        FakeControllable.controller.shift=true;
        String shifted=hints();
        check(shifted.contains("test.button.17") && shifted.contains("test.button.18"), "active Shift buttons are used");
        check(!shifted.contains("test.button.10]"), "explicitly unbound Shift button does not revive its base binding");
        FakeControllable.controller.open=false;
        check(hints().isEmpty(), "disconnected controller does not show hints");
        FakeControllable.controller.open=true;
        FakeControllable.controller.cursor=false;
        check(hints().isEmpty(), "inactive virtual cursor does not show hints");
        FakeControllable.controller.cursor=true;
        FakeRegistry.instance.broken=true;
        check(hints().isEmpty(), "optional API failure leaves the map usable");
        check(OpenMapDeltaCommon.LOGGER.warnings==1, "optional failure is reported once");
        check(hints().isEmpty() && OpenMapDeltaCommon.LOGGER.warnings==1, "failed optional adapter stays quiet");
        System.out.println("Controller support hints passed "+checks+" checks.");
    }
    private static void inject(String name,Object value) throws Exception {
        var field=ControllerSupport.class.getDeclaredField(name); field.setAccessible(true); field.set(null,value);
    }
    private static String hints() {
        return ControllerSupport.mapHints().stream().map(c->c.getString()).reduce("",(a,b)->a+b+"\n");
    }
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    public static final class FakeControllable {
        public static final FakeController controller=new FakeController();
        public static FakeController getController() { return controller; }
        public static FakeRegistry getBindingRegistry() { return FakeRegistry.instance; }
    }
    public static final class FakeController {
        public boolean open=true,cursor=true,shift;
        public boolean isOpen() { return open; }
        public boolean isUsingVirtualCursor() { return cursor; }
    }
    public static final class FakeBinding {
        public final String key; public final int button;
        FakeBinding(String key,int button) { this.key=key;this.button=button; }
        public int getButton() { return button; }
    }
    public static final class FakeRegistry {
        public static final FakeRegistry instance=new FakeRegistry();
        public final Map<String,FakeBinding> bindings=new LinkedHashMap<>();
        public final Map<String,Integer> shift=new LinkedHashMap<>(); public boolean broken;
        public void put(String key,int button) { String id="controllable.key."+key; bindings.put(id,new FakeBinding(id,button)); }
        public FakeBinding getBindingByDescriptionKey(String key) {
            if(broken) throw new IllegalStateException("optional API unavailable"); return bindings.get(key);
        }
        public int getActiveButton(FakeBinding binding,FakeController controller) {
            return controller.shift?shift.getOrDefault(binding.key,binding.button):binding.button;
        }
    }
    public static final class FakeButtons {
        public static String getNameForButton(int button) { return "test.button."+button; }
    }
}
