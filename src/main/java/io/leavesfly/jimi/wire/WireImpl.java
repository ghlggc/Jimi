package io.leavesfly.jimi.wire;

import io.leavesfly.jimi.wire.message.WireMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Wire 消息总线实现
 * 使用 Reactor Sinks 实现消息的异步传递
 * 支持通过 reset() 重置 Sink 以支持多次执行
 */
public class WireImpl implements Wire {
    
    private final AtomicReference<Sinks.Many<WireMessage>> sinkRef;
    
    public WireImpl() {
        this.sinkRef = new AtomicReference<>(createSink());
    }
    
    private Sinks.Many<WireMessage> createSink() {
        return Sinks.many().multicast().onBackpressureBuffer();
    }
    
    @Override
    public void send(WireMessage message) {
        sinkRef.get().tryEmitNext(message);
    }
    
    @Override
    public Flux<WireMessage> asFlux() {
        return sinkRef.get().asFlux();
    }
    
    @Override
    public void complete() {
        sinkRef.get().tryEmitComplete();
    }
    
    @Override
    public void reset() {
        // 创建新的 Sink，以支持新的订阅和消息发送
        sinkRef.set(createSink());
    }
}
