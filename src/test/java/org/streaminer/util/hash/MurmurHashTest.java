/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.streaminer.util.hash;

import org.streaminer.util.hash.MurmurHash;
import junit.framework.Assert;
import org.junit.Test;

/**
 * @author epollan
 */
public class MurmurHashTest
{

    @Test
    public void testHashByteArrayOverload()
    {
        String input = "hashthis";
        byte[] inputBytes = input.getBytes();

        int hashOfString = MurmurHash.getInstance().hash(input);
        Assert.assertEquals("MurmurHash.hash(byte[]) did not match MurmurHash.hash(String)",
                hashOfString, MurmurHash.getInstance().hash(inputBytes));

        Object bytesAsObject = inputBytes;
        Assert.assertEquals("MurmurHash.hash(Object) given a byte[] did not match MurmurHash.hash(String)",
                hashOfString, MurmurHash.getInstance().hash(bytesAsObject));
    }

    @Test
    public void testHash64ByteArrayOverload()
    {
        String input = "hashthis";
        byte[] inputBytes = input.getBytes();

        long hashOfString = MurmurHash.getInstance().hash64(input);
        Assert.assertEquals("MurmurHash.hash64(byte[]) did not match MurmurHash.hash64(String)",
                hashOfString, MurmurHash.getInstance().hash64(inputBytes));

        Object bytesAsObject = inputBytes;
        Assert.assertEquals("MurmurHash.hash64(Object) given a byte[] did not match MurmurHash.hash64(String)",
                hashOfString, MurmurHash.getInstance().hash64(bytesAsObject));
    }
}