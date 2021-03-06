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
package io.journalkeeper.rpc.remoting.transport.support;

import io.journalkeeper.rpc.remoting.transport.TransportServerSupport;
import io.journalkeeper.rpc.remoting.transport.config.ServerConfig;
import io.netty.channel.ChannelHandler;

/**
 * 自定义channelHandler
 * author: gaohaoxiang
 *
 * date: 2018/9/25
 */
public class ChannelTransportServer extends TransportServerSupport {

    private ChannelHandler channelHandler;

    public ChannelTransportServer(ChannelHandler channelHandler, ServerConfig serverConfig) {
        super(serverConfig);
        this.channelHandler = channelHandler;
    }

    public ChannelTransportServer(ChannelHandler channelHandler, ServerConfig serverConfig, String host) {
        super(serverConfig, host);
        this.channelHandler = channelHandler;
    }

    public ChannelTransportServer(ChannelHandler channelHandler, ServerConfig serverConfig, String host, int port) {
        super(serverConfig, host, port);
        this.channelHandler = channelHandler;
    }

    @Override
    protected ChannelHandler newChannelHandlerPipeline() {
        return this.channelHandler;
    }
}