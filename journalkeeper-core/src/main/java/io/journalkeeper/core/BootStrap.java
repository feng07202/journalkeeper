/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.journalkeeper.core;

import io.journalkeeper.base.Serializer;
import io.journalkeeper.core.api.AdminClient;
import io.journalkeeper.core.api.ClusterAccessPoint;
import io.journalkeeper.core.api.JournalEntryParser;
import io.journalkeeper.core.api.RaftClient;
import io.journalkeeper.core.api.RaftServer;
import io.journalkeeper.core.api.StateFactory;
import io.journalkeeper.core.client.ClientRpc;
import io.journalkeeper.core.client.DefaultAdminClient;
import io.journalkeeper.core.client.DefaultRaftClient;
import io.journalkeeper.core.client.RemoteClientRpc;
import io.journalkeeper.core.entry.DefaultJournalEntryParser;
import io.journalkeeper.core.server.Server;
import io.journalkeeper.rpc.RpcAccessPointFactory;
import io.journalkeeper.rpc.RpcException;
import io.journalkeeper.rpc.client.ClientServerRpcAccessPoint;
import io.journalkeeper.utils.retry.IncreasingRetryPolicy;
import io.journalkeeper.utils.retry.RetryPolicy;
import io.journalkeeper.utils.spi.ServiceSupport;
import io.journalkeeper.utils.threads.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * @author LiYue
 * Date: 2019-03-25
 */
public class BootStrap<E, ER, Q, QR> implements ClusterAccessPoint<E, ER, Q, QR> {
    private static final Logger logger = LoggerFactory.getLogger(BootStrap.class);
    private final static int SCHEDULE_EXECUTOR_THREADS = 4;

    private final StateFactory<E, ER, Q, QR> stateFactory;
    private final Serializer<E> entrySerializer;
    private final Serializer<ER> entryResultSerializer;
    private final Serializer<Q> querySerializer;
    private final Serializer<QR> queryResultSerializer;
    private final Properties properties;
    private ScheduledExecutorService scheduledExecutorService;
    private ExecutorService asyncExecutorService;
    private final RaftServer.Roll roll;
    private final RpcAccessPointFactory rpcAccessPointFactory;
    private final List<URI> servers;
    private final Server<E, ER, Q, QR> server;
    private DefaultRaftClient<E, ER, Q, QR> client = null;
    private DefaultAdminClient adminClient = null;
    private final JournalEntryParser journalEntryParser;
    private final RetryPolicy remoteRetryPolicy = new IncreasingRetryPolicy(new long [] {50, 100, 500, 1000, 3000, 10000, 30000}, 50);
    /**
     * 初始化远程模式的BootStrap，本地没有任何Server，所有操作直接请求远程Server。
     * @param servers 远程Server 列表
     * @param properties 配置属性
     */
    public BootStrap(List<URI> servers, Properties properties) {
        this(servers, null, null, null, null, properties);
    }


    /**
     * 初始化远程模式的BootStrap，本地没有任何Server，所有操作直接请求远程Server。
     * @param servers 远程Server 列表
     * @param properties 配置属性
     */
    public BootStrap(List<URI> servers,
                     Serializer<E> entrySerializer,
                     Serializer<ER> entryResultSerializer,
                     Serializer<Q> querySerializer,
                     Serializer<QR> queryResultSerializer,
                     Properties properties) {
        this(null, servers, null, entrySerializer, entryResultSerializer, querySerializer, queryResultSerializer, null,properties);
    }

    /**
     * 初始化本地Server模式BootStrap，本地包含一个Server，请求本地Server通信。
     * @param roll 本地Server的角色。
     * @param properties 配置属性
     */
    public BootStrap(RaftServer.Roll roll, StateFactory<E, ER, Q, QR> stateFactory,
                     Serializer<E> entrySerializer,
                     Serializer<ER> entryResultSerializer,
                     Serializer<Q> querySerializer,
                     Serializer<QR> queryResultSerializer,
                     Properties properties) {
        this(roll, null, stateFactory, entrySerializer, entryResultSerializer, querySerializer, queryResultSerializer, new DefaultJournalEntryParser(), properties);
    }

    /**
     * 初始化本地Server模式BootStrap，本地包含一个Server，请求本地Server通信。
     * @param roll 本地Server的角色。
     * @param properties 配置属性
     */
    public BootStrap(RaftServer.Roll roll, StateFactory<E, ER, Q, QR> stateFactory,
                     Serializer<E> entrySerializer,
                     Serializer<ER> entryResultSerializer,
                     Serializer<Q> querySerializer,
                     Serializer<QR> queryResultSerializer,
                     JournalEntryParser journalEntryParser,
                     Properties properties) {
        this(roll, null, stateFactory, entrySerializer, entryResultSerializer, querySerializer, queryResultSerializer,
                journalEntryParser, properties);
    }


    private BootStrap(RaftServer.Roll roll, List<URI> servers, StateFactory<E, ER, Q, QR> stateFactory,
                      Serializer<E> entrySerializer,
                      Serializer<ER> entryResultSerializer,
                      Serializer<Q> querySerializer,
                      Serializer<QR> queryResultSerializer,
                      JournalEntryParser journalEntryParser,
                      Properties properties) {
        this.stateFactory = stateFactory;
        this.entrySerializer = entrySerializer;
        this.entryResultSerializer = entryResultSerializer;
        this.querySerializer = querySerializer;
        this.queryResultSerializer = queryResultSerializer;
        this.properties = properties;
        this.roll = roll;
        this.rpcAccessPointFactory = ServiceSupport.load(RpcAccessPointFactory.class);
        this.journalEntryParser = journalEntryParser;
        this.server = createServer();
        this.servers = servers;
    }

    private Server<E, ER, Q, QR> createServer() {
        if(null == scheduledExecutorService) {
            this.scheduledExecutorService = Executors.newScheduledThreadPool(SCHEDULE_EXECUTOR_THREADS, new NamedThreadFactory("JournalKeeper-Scheduled-Executor"));
        }
        if(null == asyncExecutorService) {
            this.asyncExecutorService = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2, new NamedThreadFactory("JournalKeeper-Async-Executor"));
        }

        if(null != roll) {
            return new Server<>(roll,stateFactory,entrySerializer, entryResultSerializer, querySerializer, queryResultSerializer, journalEntryParser, scheduledExecutorService, asyncExecutorService, properties);
        }
        return null;
    }

    @Override
    public RaftClient<E, ER, Q, QR> getClient() {
        if(null == client) {
            RemoteClientRpc clientRpc = createRemoteClientRpc();
            client = new DefaultRaftClient<>(clientRpc, entrySerializer, entryResultSerializer, querySerializer, queryResultSerializer, properties);
        }
        return client;
    }

    private RemoteClientRpc createRemoteClientRpc() {
        ClientServerRpcAccessPoint clientServerRpcAccessPoint = rpcAccessPointFactory.createClientServerRpcAccessPoint(this.properties);
        RemoteClientRpc clientRpc;
        if(this.server == null) {
            clientRpc = new RemoteClientRpc(getServersForClient(), clientServerRpcAccessPoint, remoteRetryPolicy, asyncExecutorService);
        } else {
            clientServerRpcAccessPoint = new LocalDefaultRpcAccessPoint(server, clientServerRpcAccessPoint);
            clientRpc = new RemoteClientRpc(getServersForClient(), clientServerRpcAccessPoint, remoteRetryPolicy, asyncExecutorService);
            clientRpc.setPreferredServer(server.serverUri());
        }
        return clientRpc;
    }

    public void shutdown() {
        if(null != client) {
            client.stop();
        }
        if(null != adminClient) {
            adminClient.stop();
        }
        if(null != server ) {
            server.stop();
        }
        if (null != scheduledExecutorService) {
            this.scheduledExecutorService.shutdown();
        }
        if (null != asyncExecutorService) {
            this.asyncExecutorService.shutdown();
        }
    }

    @Override
    public RaftServer getServer() {
        return server;
    }

    @Override
    public AdminClient getAdminClient() {
        if(null == adminClient) {
            ClientRpc clientRpc = createRemoteClientRpc();
            adminClient = new DefaultAdminClient(clientRpc, properties);
        }
        return adminClient;
    }


    private List<URI> getServersForClient() {
        if(null == server) {
            return servers;
        } else {
            try {
                return server.getServers().get().getClusterConfiguration().getVoters();
            } catch (Throwable e) {
                throw new RpcException(e);
            }
        }
    }
}
