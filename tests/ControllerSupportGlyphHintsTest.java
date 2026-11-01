package net.aosankaku.openmapdelta.compat;

import net.minecraft.client.Minecraft;
import net.aosankaku.openmapdelta.gui.FullscreenMapScreen;
import com.mrcrayfish.controllable.client.util.ClientHelper;
import static net.aosankaku.openmapdelta.compat.ControllerSupportHintsTest.*;

public final class ControllerSupportGlyphHintsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(ControllerSupport.mapHints().isEmpty(), "missing controller integration has no hints");
        inject("controllable", FakeControllable.class);
        inject("buttons", FakeButtons.class);
        inject("bindingRegistry", FakeRegistry.class);
        Minecraft.getInstance().screen=new FullscreenMapScreen();
        FakeRegistry.instance.put("pickup_item",1); FakeRegistry.instance.put("split_stack",2);
        FakeRegistry.instance.put("scroll_up",3); FakeRegistry.instance.put("scroll_down",4);
        FakeRegistry.instance.put("close_inventory",5);
        var legacy=ControllerSupport.mapHints();
        check(legacy.size()==4 && legacy.stream().allMatch(c->c.buttonGlyph),
                "older Controllable's generic bindings also use image glyphs");
        FakeRegistry.instance.bindings.clear();
        for(String action:new String[]{"confirm","context","context_alternate","pan_up","pan_down","pan_left",
                "pan_right","recenter","toggle_waypoints","zoom_in","zoom_in_alternate","zoom_out","zoom_out_alternate","close"}) {
            FakeRegistry.instance.put("openmap."+action, action.equals("context_alternate")?1:1+FakeRegistry.instance.bindings.size());
        }
        var hints=ControllerSupport.mapHints();
        check(!hints.isEmpty() && hints.stream().allMatch(c->c.buttonGlyph), "map buttons retain Controllable image-font components");
        check(hints.stream().noneMatch(c->c.getString().contains("test.button.")), "button names are not printed as text");
        for(int button:new int[]{1,2,4,5,6,7,8,9,10,11,12,13,14})
            check(text().contains("test.glyph.0."+button), "active glyph is included for "+button);
        ClientHelper.theme=3;
        check(text().contains("test.glyph.3."), "selected Controllable icon theme is read each time");
        FakeControllable.controller.shift=true;
        FakeRegistry.instance.shift.put("controllable.key.openmap.context",25);
        FakeRegistry.instance.shift.put("controllable.key.openmap.context_alternate",-1);
        check(text().contains("test.glyph.3.25"), "Shift remapping selects the matching image");
        var context=ControllerSupport.mapHints().stream().filter(c->c.getString().contains("controller.context")).findFirst().orElseThrow();
        check(!context.getString().contains("test.glyph.3.1]"), "explicit unbound alternate does not revive a base button");
        FakeControllable.controller.open=false;
        check(ControllerSupport.mapHints().isEmpty(), "disconnected controller hides hints");
        FakeControllable.controller.open=true; FakeControllable.controller.cursor=false;
        check(ControllerSupport.mapHints().isEmpty(), "keyboard use hides hints");
        FakeControllable.controller.cursor=true;
        FakeRegistry.instance.broken=true;
        check(ControllerSupport.mapHints().isEmpty(), "incompatible optional API cannot break the map");
        check(net.aosankaku.openmapdelta.OpenMapDeltaCommon.LOGGER.warnings==1, "optional failure is reported once");
        check(ControllerSupport.mapHints().isEmpty(), "failed optional integration stays silent");
        System.out.println("Controller glyph hint checks passed ("+checks+").");
    }
    private static String text() { return ControllerSupport.mapHints().toString(); }
    private static void inject(String name,Object value) throws Exception {
        var field=ControllerSupport.class.getDeclaredField(name); field.setAccessible(true); field.set(null,value);
    }
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
}
