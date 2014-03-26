package db.infra;

import com.google.common.collect.ImmutableMap;
import events.ChangePair;
import events.EventsStream;
import events.PushStream;
import events.StreamRegisterer;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @param <K> parent primary key
 * @param <V> parent type
 */
public class Denormalizer<K, V extends Indexed<K>> {

    private final IReadCache<K, V> parentCache;
    private final StreamRegisterer<ChangePair<DenormalizedEntity<V>>> output;
    private final PushStream<ChangePair<DenormalizedEntity<V>>> childChangeOuput;
    private final Function<V, DenormalizedEntity<V>> denormFunction;
    private final List<ChildHandler> childHandlers;

    public Denormalizer(IReadCache<K, V> parentCache, SubEntityDef... subEntitiesDefs) {

        this.parentCache = parentCache;
        this.childChangeOuput = new PushStream<>();
        this.childHandlers = Arrays.asList(subEntitiesDefs).stream().map(def -> new ChildHandler(def)).collect(Collectors.toList());
        this.denormFunction = normParent -> normParent == null ? null
                : new DenormalizedEntity<>(normParent,  ImmutableMap.copyOf(Arrays.asList(subEntitiesDefs).stream().collect(Collectors.toMap(def -> def.name, def -> def.cache.get(def.indexer.apply(normParent))))));
        this.output = new StreamRegisterer<ChangePair<DenormalizedEntity<V>>>() {
            private Consumer<ChangePair<V>> SubEntityAdder;

            @Override
            public void register(Consumer<ChangePair<DenormalizedEntity<V>>> c) {
                SubEntityAdder = parentChange -> c.accept(
                        new ChangePair<>(denormFunction.apply(parentChange.getOld()), denormFunction.apply(parentChange.getNew())));
                parentCache.getOutput().register(SubEntityAdder);
                childChangeOuput.register(c);
            }

            @Override
            public void unRegister(Consumer<ChangePair<DenormalizedEntity<V>>> c) {
                parentCache.getOutput().unRegister(SubEntityAdder);
                childChangeOuput.unRegister(c);
            }
        };
    }

    public Denormalizer<K, V> start() {
        childHandlers.stream().forEach(childHandler -> childHandler.start());
        return this;
    }

    public void stop() {
        childHandlers.stream().forEach(childHandler -> childHandler.stop());
    }

    public EventsStream<ChangePair<DenormalizedEntity<V>>> output() {
        return output;
    }

    private class ChildHandler<K2, V2 extends Indexed<K2>> {

        private final Index<K, V, K2> index;
        private final Consumer<ChangePair<V2>> childChangeInput;
        private final IReadCache<K2, V2> childCache;

        public ChildHandler(SubEntityDef<V, K2, V2> def) {
            this.childCache = def.cache;
            this.index = new Index<>(def.indexer);
            this.childChangeInput = (ChangePair<V2> childChange) -> {
                K2 childKey = childChange.getOld()!= null ? childChange.getOld().getId() : childChange.getNew().getId();
                index.getAll(childKey).stream().forEach(parentKey -> {
                    DenormalizedEntity<V> parentDenorm = denormFunction.apply(parentCache.get(parentKey));
                    childChangeOuput.publish(new ChangePair<>(parentDenorm.replace(def.name, childChange.getOld()),
                            parentDenorm.replace(def.name, childChange.getNew())));
                });
            };
        }

        private void start() {
            parentCache.getOutput().register(index.input());
            childCache.getOutput().register(childChangeInput);
        }

        private void stop() {
            parentCache.getOutput().unRegister(index.input());
            childCache.getOutput().unRegister(childChangeInput);
        }
    }

    public static class SubEntityDef<V, K2, V2 extends Indexed<K2>> {

        final String name;
        final Class<? extends V2> c;
        final IReadCache<K2, V2> cache;
        final Function<V, K2> indexer;

        public SubEntityDef(String name, Class<? extends V2> c, IReadCache<K2, V2> cache, Function<V, K2> indexer) {
            this.name = name;
            this.c = c;
            this.cache = cache;
            this.indexer = indexer;
        }
    }
}
