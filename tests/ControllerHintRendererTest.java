package net.aosankaku.openmapdelta.gui;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mrcrayfish.controllable.client.util.ClientHelper;
public final class ControllerHintRendererTest {
    public static void main(String[] args) {
        var hint=ClientHelper.getButtonComponent(1).append(" : action with a long label");
        var graphics=new GuiGraphicsExtractor();
        int drawn=ControllerHintRenderer.draw(graphics,new Font(),List.of(hint,hint,hint),10,20,30,52);
        check(drawn==2 && graphics.rows==2, "glyph rows fit the actual viewport height");
        check(graphics.glyphs, "styled clipping and drawing preserve the asset font");
        check(graphics.width<=26, "long translated hints remain inside the viewport");
        check(ControllerHintRenderer.draw(graphics,new Font(),List.of(hint),0,0,5,52)==0, "narrow viewport draws nothing");
        check(ControllerHintRenderer.draw(graphics,new Font(),List.of(hint),0,0,30,20)==0, "short viewport draws nothing");
        System.out.println("Controller hint renderer checks passed (5).");
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
