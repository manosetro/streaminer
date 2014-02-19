/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.streaminer.stream.frequency;

import org.streaminer.stream.membership.Filter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.streaminer.util.hash.HashUtils;

/**
 * Count-Min Sketch datastructure.
 * An Improved Data Stream Summary: The Count-Min Sketch and its Applications
 * http://www.eecs.harvard.edu/~michaelm/CS222/countmin.pdf
 */
public class CountMinSketchAlt implements ISimpleFrequency<Object> {
    public static final long PRIME_MODULUS = (1L << 31) - 1;
    private int depth;
    private int width;
    private long[][] table;
    private long[] hashA;
    private long size;
    private double eps;
    private double confidence;

    private CountMinSketchAlt() {
    }

    public CountMinSketchAlt(int depth, int width, int seed) {
        this.depth = depth;
        this.width = width;
        this.eps = 2.0 / width;
        this.confidence = 1 - 1 / Math.pow(2, depth);
        initTablesWith(depth, width, seed);
    }

    public CountMinSketchAlt(double epsOfTotalCount, double confidence, int seed) {
        // 2/w = eps ; w = 2/eps
        // 1/2^depth <= 1-confidence ; depth >= -log2 (1-confidence)
        this.eps = epsOfTotalCount;
        this.confidence = confidence;
        this.width = (int) Math.ceil(2 / epsOfTotalCount);
        this.depth = (int) Math.ceil(-Math.log(1 - confidence) / Math.log(2));
        initTablesWith(depth, width, seed);
    }

    private CountMinSketchAlt(int depth, int width, int size, long[] hashA, long[][] table) {
        this.depth = depth;
        this.width = width;
        this.eps   = 2.0 / width;
        this.confidence = 1 - 1 / Math.pow(2, depth);
        this.hashA = hashA;
        this.table = table;
        this.size  = size;
    }

    private void initTablesWith(int depth, int width, int seed) {
        this.table = new long[depth][width];
        this.hashA = new long[depth];
        Random r = new Random(seed);
        // We're using a linear hash functions
        // of the form (a*x+b) mod p.
        // a,b are chosen independently for each hash function.
        // However we can set b = 0 as all it does is shift the results
        // without compromising their uniformity or independence with
        // the other hashes.
        for (int i = 0; i < depth; ++i) {
            hashA[i] = r.nextInt(Integer.MAX_VALUE);
        }
    }

    public double getRelativeError() {
        return eps;
    }

    public double getConfidence() {
        return confidence;
    }

    private int hash(long item, int i) {
        long hash = hashA[i] * item;
        // A super fast way of computing x mod 2^p-1
        // See http://www.cs.princeton.edu/courses/archive/fall09/cos521/Handouts/universalclasses.pdf
        // page 149, right after Proposition 7.
        hash += hash >> 32;
        hash &= PRIME_MODULUS;
        // Doing "%" after (int) conversion is ~2x faster than %'ing longs.
        return ((int) hash) % width;
    }
    
    @Override
    public boolean add(Object item) throws FrequencyException {
        add(item, 1);
        return true;
    }

    @Override
    public boolean add(Object item, long count) throws FrequencyException {
        if (count < 0) {
            // Actually for negative increments we'll need to use the median
            // instead of minimum, and accuracy will suffer somewhat.
            // Probably makes sense to add an "allow negative increments"
            // parameter to constructor.
            throw new IllegalArgumentException("Negative increments not implemented");
        }
        
        if (item instanceof Integer) {
            addLong(((Integer)item).longValue(), count);
        } else if (item instanceof Long) {
            addLong((Long)item, count);
        } else if (item instanceof String) {
            int[] buckets = HashUtils.getHashBuckets((String)item, depth, width);
            for (int i = 0; i < depth; ++i) {
                table[i][buckets[i]] += count;
            }
            size += count;
        }
        
        return true;
    }

    @Override
    public long estimateCount(Object item) {
        if (item instanceof Integer) {
            estimateCountLong(((Integer)item).longValue());
        } else if (item instanceof Long) {
            estimateCountLong((Long)item);
        } else if (item instanceof String) {
            long res = Long.MAX_VALUE;
            int[] buckets = HashUtils.getHashBuckets((String)item, depth, width);
            for (int i = 0; i < depth; ++i) {
                res = Math.min(res, table[i][buckets[i]]);
            }
            return res;
        }
        
        return 0L;
    }

    @Override
    public long size() {
        return size;
    }
    
    private void addLong(long item, long count) {
        for (int i = 0; i < depth; ++i) {
            table[i][hash((Long)item, i)] += count;
        }
        size += count;
    }
    
    private long estimateCountLong(long item) {
        long res = Long.MAX_VALUE;
        for (int i = 0; i < depth; ++i) {
            res = Math.min(res, table[i][hash((Long)item, i)]);
        }
        return res;
    }
    
    /**
     * Merges count min sketches to produce a count min sketch for their combined streams
     *
     * @param estimators
     * @return merged estimator or null if no estimators were provided
     * @throws CMSMergeException if estimators are not mergeable (same depth, width and seed)
     */
    public static CountMinSketchAlt merge(CountMinSketchAlt... estimators) throws CMSMergeException {
        CountMinSketchAlt merged = null;
        if (estimators != null && estimators.length > 0) {
            int depth = estimators[0].depth;
            int width = estimators[0].width;
            long[] hashA = Arrays.copyOf(estimators[0].hashA, estimators[0].hashA.length);

            long[][] table = new long[depth][width];
            int size = 0;

            for (CountMinSketchAlt estimator : estimators) {
                if (estimator.depth != depth) {
                    throw new CMSMergeException("Cannot merge estimators of different depth");
                }
                if (estimator.width != width) {
                    throw new CMSMergeException("Cannot merge estimators of different width");
                }
                if (!Arrays.equals(estimator.hashA, hashA)) {
                    throw new CMSMergeException("Cannot merge estimators of different seed");
                }

                for (int i = 0; i < table.length; i++) {
                    for (int j = 0; j < table[i].length; j++) {
                        table[i][j] += estimator.table[i][j];
                    }
                }
                size += estimator.size;
            }

            merged = new CountMinSketchAlt(depth, width, size, hashA, table);
        }

        return merged;
    }

    public static byte[] serialize(CountMinSketchAlt sketch) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream s = new DataOutputStream(bos);
        try {
            s.writeLong(sketch.size);
            s.writeInt(sketch.depth);
            s.writeInt(sketch.width);
            for (int i = 0; i < sketch.depth; ++i) {
                s.writeLong(sketch.hashA[i]);
                for (int j = 0; j < sketch.width; ++j) {
                    s.writeLong(sketch.table[i][j]);
                }
            }
            return bos.toByteArray();
        } catch (IOException e) {
            // Shouldn't happen
            throw new RuntimeException(e);
        }
    }

    public static CountMinSketchAlt deserialize(byte[] data) {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        DataInputStream s = new DataInputStream(bis);
        try {
            CountMinSketchAlt sketch = new CountMinSketchAlt();
            sketch.size = s.readLong();
            sketch.depth = s.readInt();
            sketch.width = s.readInt();
            sketch.eps = 2.0 / sketch.width;
            sketch.confidence = 1 - 1 / Math.pow(2, sketch.depth);
            sketch.hashA = new long[sketch.depth];
            sketch.table = new long[sketch.depth][sketch.width];
            for (int i = 0; i < sketch.depth; ++i) {
                sketch.hashA[i] = s.readLong();
                for (int j = 0; j < sketch.width; ++j) {
                    sketch.table[i][j] = s.readLong();
                }
            }
            return sketch;
        } catch (IOException e) {
            // Shouldn't happen
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("serial")
    protected static class CMSMergeException extends FrequencyException {
        public CMSMergeException(String message) {
            super(message);
        }
    }
}
