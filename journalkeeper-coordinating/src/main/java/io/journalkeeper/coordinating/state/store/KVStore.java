/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.journalkeeper.coordinating.state.store;

import java.util.List;

/**
 * KVStore
 * author: gaohaoxiang
 *
 * date: 2019/5/30
 */
public interface KVStore {

    boolean set(byte[] key, byte[] value);

    byte[] get(byte[] key);

    List<byte[]> multiGet(List<byte[]> keys);

    boolean exist(byte[] key);

    boolean remove(byte[] key);

    boolean compareAndSet(byte[] key, byte[] expect, byte[] update);

    void close();

    void flush();
}