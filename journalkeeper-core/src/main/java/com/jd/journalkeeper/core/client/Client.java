package com.jd.journalkeeper.core.client;

import com.jd.journalkeeper.base.Serializer;
import com.jd.journalkeeper.base.event.EventWatcher;
import com.jd.journalkeeper.core.api.ClusterConfiguration;
import com.jd.journalkeeper.core.api.JournalKeeperClient;
import com.jd.journalkeeper.core.exception.NoLeaderException;
import com.jd.journalkeeper.exceptions.NotLeaderException;
import com.jd.journalkeeper.rpc.BaseResponse;
import com.jd.journalkeeper.rpc.LeaderResponse;
import com.jd.journalkeeper.rpc.RpcException;
import com.jd.journalkeeper.rpc.StatusCode;
import com.jd.journalkeeper.rpc.client.*;
import com.jd.journalkeeper.utils.state.StateServer;

import java.io.IOException;
import java.net.URI;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 客户端实现
 * @author liyue25
 * Date: 2019-03-25
 */
public class Client<E, Q, R> implements JournalKeeperClient<E, Q, R> {

    private final ClientServerRpcAccessPoint clientServerRpcAccessPoint;
    private final Properties properties;
    private final Serializer<E> entrySerializer;
    private final Serializer<Q> querySerializer;
    private final Serializer<R> resultSerializer;

    public Client(ClientServerRpcAccessPoint clientServerRpcAccessPoint, Serializer<E> entrySerializer, Serializer<Q> querySerializer,
                  Serializer<R> resultSerializer, Properties properties) {
        this.clientServerRpcAccessPoint = clientServerRpcAccessPoint;
        this.entrySerializer = entrySerializer;
        this.querySerializer = querySerializer;
        this.resultSerializer = resultSerializer;
        this.properties = properties;
    }

    @Override
    public CompletableFuture<Void> update(E entry) {
        return invokeLeaderRpc(
                leaderRpc -> leaderRpc.updateClusterState(new UpdateClusterStateRequest(entrySerializer.serialize(entry))))
                .thenAccept(resp -> {});
    }


    @Override
    public CompletableFuture<R> query(Q query) {
        return invokeLeaderRpc(
                leaderRpc -> leaderRpc.queryClusterState(new QueryStateRequest(querySerializer.serialize(query))))
                .thenApply(QueryStateResponse::getResult)
                .thenApply(resultSerializer::parse);
    }

    @Override
    public CompletableFuture<ClusterConfiguration> getServers() {
        return clientServerRpcAccessPoint.getClintServerRpc()
                .getServers()
                .thenApply(GetServersResponse::getClusterConfiguration);
    }

    @Override
    public CompletableFuture<Boolean> updateVoters(UpdateVoterOperation operation, URI voter) {
        // TODO 变更集群配置
        return null;
    }

    @Override
    public void watch(EventWatcher eventWatcher) {
        // TODO 事件通知
    }

    @Override
    public void unwatch(EventWatcher eventWatcher) {

    }

    //TODO: 根据配置选择：
    // 1. 去LEADER上直接读取 <-- 现在用的是这种
    // 2. 二步读取

    //FIXME：考虑这种情况：A，B2个server，A认为B是leader， B认为A是leader，此时会出现死循环。

    /**
     * 去Leader上请求数据，如果返回NotLeaderException，更换Leader重试。
     *
     * @param invoke 真正去Leader要调用的方法
     * @param <T> 返回的Response
     */
    private <T extends LeaderResponse> CompletableFuture<T> invokeLeaderRpc(LeaderRpc<T, E, Q, R> invoke) {
        return getLeaderRpc()
                .thenCompose(invoke::invokeLeader)
                .thenCompose(resp -> {
                    if (resp.getStatusCode() == StatusCode.NOT_LEADER) {
                        return invoke.invokeLeader(clientServerRpcAccessPoint.getClintServerRpc(resp.getLeader()));
                    } else {
                        return CompletableFuture.supplyAsync(() -> resp);
                    }
                });
    }

    private interface LeaderRpc<T extends BaseResponse, E, Q, R> {
        CompletableFuture<T> invokeLeader(ClientServerRpc leaderRpc);
    }

    private CompletableFuture<ClientServerRpc> getLeaderRpc() {
        return clientServerRpcAccessPoint
                .getClintServerRpc()
                .getServers()
                .exceptionally(GetServersResponse::new)
                .thenApply(resp -> {
                    if(resp.success()) {
                        if(resp.getClusterConfiguration() != null && resp.getClusterConfiguration().getLeader() != null) {
                            return clientServerRpcAccessPoint.getClintServerRpc(
                                    resp.getClusterConfiguration().getLeader());
                        } else {
                            throw new NoLeaderException();
                        }
                    } else {
                         throw new RpcException(resp);
                    }
                });
    }


    public void stop() {
        clientServerRpcAccessPoint.stop();
    }
}
