package com.wxwr.kaleidoscopeagricultureevolution.genetics.genome;

import com.wxwr.kaleidoscopeagricultureevolution.genetics.species.CropSpecies;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class PhenotypeCache {
    static final int MAX_SIZE = 4096;

    /**
     * LRU 表现型缓存。
     *
     * <p><b>必须同步</b>：本缓存是静态的，同时被逻辑服务端（生长 / 收获 / 授粉）与
     * 逻辑客户端（种子 tooltip、方块与物品着色）访问。accessOrder 的
     * {@link LinkedHashMap} 在 {@code get} 时会改动内部链表，并发访问会破坏其结构。
     * {@link Collections#synchronizedMap} 会同步 {@code computeIfAbsent} 等默认方法，
     * 且映射函数（纯计算）在锁内执行、不会重入本缓存，因此不存在死锁风险。
     */
    static final Map<Key, Phenotype> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Key, Phenotype> eldest) {
                    return size() > MAX_SIZE;
                }
            });

    public static Phenotype get(Genome genome, CropSpecies species) {
        Key key = new Key(genome, species);
        return cache.computeIfAbsent(key, k -> Phenotype.evaluate(k.genome, k.species));
    }

    public static void clear() {
        cache.clear();
    }

    public static int size() {
        return cache.size();
    }

    private static class Key {
        final Genome genome;
        final CropSpecies species;

        Key(Genome genome, CropSpecies species) {
            this.genome = genome;
            this.species = species;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key other)) return false;
            return genome.equals(other.genome) && species.equals(other.species);
        }

        @Override
        public int hashCode() {
            return genome.hashCode() * 31 + species.hashCode();
        }
    }
}
