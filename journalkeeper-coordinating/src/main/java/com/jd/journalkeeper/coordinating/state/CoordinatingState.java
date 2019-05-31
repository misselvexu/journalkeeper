package com.jd.journalkeeper.coordinating.state;

import com.jd.journalkeeper.coordinating.state.domain.StateCodes;
import com.jd.journalkeeper.coordinating.state.domain.StateReadRequest;
import com.jd.journalkeeper.coordinating.state.domain.StateResponse;
import com.jd.journalkeeper.coordinating.state.domain.StateTypes;
import com.jd.journalkeeper.coordinating.state.domain.StateWriteRequest;
import com.jd.journalkeeper.coordinating.state.config.StateConfigs;
import com.jd.journalkeeper.coordinating.state.store.KVStore;
import com.jd.journalkeeper.coordinating.state.store.KVStoreManager;
import com.jd.journalkeeper.core.api.RaftJournal;
import com.jd.journalkeeper.core.api.StateFactory;
import com.jd.journalkeeper.core.state.LocalState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * CoordinatingState
 * author: gaohaoxiang
 * email: gaohaoxiang@jd.com
 * date: 2019/5/30
 */
// TODO 操作拆分
public class CoordinatingState extends LocalState<StateWriteRequest, StateReadRequest, StateResponse> {

    private Properties properties;
    private KVStore kvStore;

    protected CoordinatingState(StateFactory<StateWriteRequest, StateReadRequest, StateResponse> stateFactory) {
        super(stateFactory);
    }

    @Override
    protected void recoverLocalState(Path path, RaftJournal raftJournal, Properties properties) throws IOException {
        this.properties = properties;
        this.kvStore = KVStoreManager.getFactory(properties.getProperty(StateConfigs.STORE_TYPE)).create(path, properties);
    }

    @Override
    public Map<String, String> execute(StateWriteRequest entry, int partition, long index, int batchSize) {
        StateTypes type = StateTypes.valueOf(entry.getType());
        switch (type) {
            case PUT: {
                kvStore.put(entry.getKey(), entry.getValue());
                break;
            }
            case REMOVE: {
                kvStore.remove(entry.getKey());
                break;
            }
            case COMPARE_AND_SET: {
                kvStore.compareAndSet(entry.getKey(), entry.getExpect(), entry.getValue());
                break;
            }
        }
        return null;
    }

    @Override
    public CompletableFuture<StateResponse> query(StateReadRequest query) {
        StateTypes type = StateTypes.valueOf(query.getType());
        switch (type) {
            case GET: {
                return CompletableFuture.supplyAsync(() -> {
                    byte[] value = kvStore.get(query.getKey());
                    return new StateResponse(StateCodes.SUCCESS.getCode(), value);
                });
            }
        }
        return null;
    }
}
