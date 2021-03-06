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
package io.journalkeeper.coordinating.state.store.rocksdb;

import io.journalkeeper.coordinating.exception.CoordinatingException;
import io.journalkeeper.coordinating.state.exception.CoordinatingStateException;
import io.journalkeeper.coordinating.state.store.KVStore;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * RocksDBKVStore
 * author: gaohaoxiang
 *
 * date: 2019/5/30
 */
public class RocksDBKVStore implements KVStore {

    protected static final Logger logger = LoggerFactory.getLogger(RocksDBKVStore.class);
    private static final StringBuilder STRING_BUILDER_CACHE = new StringBuilder();

    static {
        RocksDB.loadLibrary();
    }

    private Path path;
    private Properties properties;
    private RocksDB rocksDB;

    public RocksDBKVStore(Path path, Properties properties) {
        this.path = path;
        this.properties = properties;
        this.rocksDB = init(path, properties);
    }

    protected RocksDB init(Path path, Properties properties) {
        try {
            Options options = parseOptions(properties);
            return RocksDB.open(options, path.toString());
        } catch (Exception e) {
            throw new CoordinatingStateException(e);
        }
    }

    protected Options parseOptions(Properties properties) {
        return RocksDBConfigParser.parse(properties);
    }

    @Override
    public boolean set(byte[] key, byte[] value) {
        try {
            rocksDB.put(key, value);
            return true;
        } catch (RocksDBException e) {
            throw new CoordinatingStateException(e);
        }
    }

    @Override
    public byte[] get(byte[] key) {
        try {
            return rocksDB.get(key);
        } catch (RocksDBException e) {
            throw new CoordinatingStateException(e);
        }
    }

    @Override
    public List<byte[]> multiGet(List<byte[]> keys) {
        try {
            return rocksDB.multiGetAsList(keys);
        } catch (RocksDBException e) {
            throw new CoordinatingStateException(e);
        }
    }

    @Override
    public boolean exist(byte[] key) {
        return rocksDB.keyMayExist(key, STRING_BUILDER_CACHE);
    }

    @Override
    public boolean remove(byte[] key) {
        try {
            if (!rocksDB.keyMayExist(key, STRING_BUILDER_CACHE)) {
                return false;
            }
            rocksDB.delete(key);
            return true;
        } catch (RocksDBException e) {
            throw new CoordinatingStateException(e);
        }
    }

    @Override
    public boolean compareAndSet(byte[] key, byte[] expect, byte[] update) {
        try {
            byte[] current = rocksDB.get(key);
            if (current != null && !Objects.deepEquals(current, expect)) {
                return false;
            }
            rocksDB.put(key, update);
            return true;
        } catch (RocksDBException e) {
            throw new CoordinatingStateException(e);
        }
    }

    @Override
    public void close() {
        rocksDB.close();
    }

    @Override
    public void flush() {
        try {
            rocksDB.flush(new FlushOptions());
        } catch (RocksDBException e) {
            throw new CoordinatingException(e);
        }
    }
}