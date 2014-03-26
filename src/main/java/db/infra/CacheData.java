package db.infra;

import events.ChangePair;
import events.EventsStream;
import events.PushStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * @param <K>
 * @param <V>
 */
public class CacheData<K, V extends Indexed<K>> implements IReadCache<K,V> {

    ConcurrentHashMap<K, V> map;
    PushStream<ChangePair<V>> output;

    Consumer<ChangeEvent<V>> inputConsumer;
    private final EventsStream<ChangeEvent<V>> updatesStream;

    public CacheData(EventsStream<ChangeEvent<V>> updatesStream,ExecutorService exec) {
        map = new ConcurrentHashMap<>();
        output  = new PushStream<ChangePair<V>>() {
            @Override
            public void register(Consumer<ChangePair<V>> c) {                
                exec.execute(() -> {
                    System.out.println("registering to cache "+c);
                    map.values().stream().forEach(
                            v->c.accept(new ChangePair<>(null,v))
                    );
                    super.register(c);
                });
            }
        };
        this.updatesStream = updatesStream;
        this.inputConsumer = t -> {
            switch (t.getType()) {
                case update:
                    output.publish(new ChangePair<>(map.put(t.getEntity().getId(), t.getEntity()), t.getEntity()));
                    break;
                case delete:
                    output.publish(new ChangePair<>(map.remove(t.getEntity().getId()), null));
                    break;
                default:
                    throw new RuntimeException();
            }
        };
    }

    public CacheData<K,V> start() {
        updatesStream.register(inputConsumer);
        return this;
    }
    
    public CacheData<K,V> stop() {
        updatesStream.unRegister(inputConsumer);
        return this;
    }


    @Override
    public EventsStream<ChangePair<V>> getOutput() {
        return output.registerThrough();
    }

    @Override
    public V get(K key) {
        return map.get(key);
    }

}
