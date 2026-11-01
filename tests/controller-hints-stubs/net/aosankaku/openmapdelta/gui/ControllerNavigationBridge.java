package net.aosankaku.openmapdelta.gui;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
public final class ControllerNavigationBridge {
    public boolean connect(ClassLoader loader,Supplier<List<ControllerNavigationLayout.Point>> provider,
                           Consumer<Throwable> failure) { return false; }
}
