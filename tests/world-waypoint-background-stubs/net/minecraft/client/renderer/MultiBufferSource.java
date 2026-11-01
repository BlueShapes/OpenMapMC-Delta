package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;

public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType type);

    final class BufferSource implements MultiBufferSource {
        private final VertexConsumer consumer;
        public RenderType requested;
        public RenderType flushed;
        public int flushes;

        public BufferSource(VertexConsumer consumer) { this.consumer = consumer; }
        public VertexConsumer getBuffer(RenderType type) { requested = type; return consumer; }
        public void endBatch(RenderType type) { flushed = type; flushes++; }
    }
}
