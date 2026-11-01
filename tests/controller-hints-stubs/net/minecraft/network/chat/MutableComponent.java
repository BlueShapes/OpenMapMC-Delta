package net.minecraft.network.chat;
public final class MutableComponent extends Component {
    MutableComponent(String value) { super(value); }
    public MutableComponent append(String text) { value+=text; return this; }
    public MutableComponent append(Component other) { value+=other.getString(); buttonGlyph|=other.buttonGlyph; return this; }
}
