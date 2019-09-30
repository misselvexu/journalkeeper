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
package io.journalkeeper.rpc.utils;

import io.journalkeeper.rpc.BaseResponse;
import io.journalkeeper.rpc.header.JournalKeeperHeader;
import io.journalkeeper.rpc.payload.GenericPayload;
import io.journalkeeper.rpc.payload.VoidPayload;
import io.journalkeeper.rpc.remoting.transport.Transport;
import io.journalkeeper.rpc.remoting.transport.command.Command;
import io.journalkeeper.rpc.remoting.transport.command.CommandCallback;
import io.journalkeeper.rpc.remoting.transport.command.Direction;
import io.journalkeeper.rpc.remoting.transport.command.Header;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author LiYue
 * Date: 2019-04-01
 */
public class CommandSupport {
    private static <T> Command newRequestCommand(int type, T request, URI destination){
        JournalKeeperHeader header = new JournalKeeperHeader(Direction.REQUEST, type, destination);
        return new Command(header, new GenericPayload<>(request));
    }

    private static Command newRequestCommand(int type, URI destination){
        JournalKeeperHeader header = new JournalKeeperHeader(Direction.REQUEST, type, destination);
        return new Command(header, new VoidPayload());
    }

    public static <Q, R extends BaseResponse> CompletableFuture<R> sendRequest(Q request, int requestType, Transport transport, URI destination) {
        CompletableFuture<R> future = new CompletableFuture<>();
//        try {
//            Command response = transport.sync(null == request ? newRequestCommand(requestType, destination): newRequestCommand(requestType, request, destination));
//            future.complete(GenericPayload.get(response.getPayload()));
//        } catch (Exception e) {
//            future.completeExceptionally(e);
//        }

        transport.async(
                null == request ? newRequestCommand(requestType, destination): newRequestCommand(requestType, request, destination),
                new CommandCallback() {
                    @Override
                    public void onSuccess(Command request, Command response) {
                        future.complete(GenericPayload.get(response.getPayload()));
                    }

                    @Override
                    public void onException(Command request, Throwable cause) {
                        future.completeExceptionally(cause);
                    }
                });
        return  future;
    }

    public static void sendResponse(BaseResponse response, int responseType,  Command requestCommand, Transport transport) {
        Command responseCommand = newResponseCommand(response, responseType, requestCommand);
        transport.acknowledge(requestCommand, responseCommand);
    }

    public static Command newResponseCommand(BaseResponse response, int responseType, Command requestCommand) {
        Header requestHeader = requestCommand.getHeader();
        JournalKeeperHeader header = new JournalKeeperHeader(Direction.RESPONSE, requestHeader.getRequestId(), responseType, null);
        header.setStatus(response.getStatusCode().getCode());
        header.setError(response.getError());

        return new Command(header,new GenericPayload<>(response));
    }
}
