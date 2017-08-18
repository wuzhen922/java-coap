/**
 * Copyright (C) 2011-2017 ARM Limited. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
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
package com.mbed.coap.server.internal;

import static com.mbed.coap.server.internal.CoapServerUtils.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.BlockSize;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.SignalingOptions;
import com.mbed.coap.server.CoapTcpCSMStorage;
import com.mbed.coap.transport.CoapReceiverForTcp;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import com.mbed.coap.utils.Callback;
import com.mbed.coap.utils.RequestCallback;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Coap server for reliable transport (draft-ietf-core-coap-tcp-tls-09)
 */
public class CoapTcpMessaging extends CoapMessaging implements CoapReceiverForTcp {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapTcpMessaging.class);

    private static final List<BlockSize> REVERSED_BLOCK_SIZES = Arrays.asList(BlockSize.values());

    static {
        Collections.reverse(REVERSED_BLOCK_SIZES);
    }

    private final ConcurrentMap<DelayedTransactionId, RequestCallback> transactions = new ConcurrentHashMap<>();
    private final CoapTcpCSMStorage csmStorage;
    private BlockSize blockSize = BlockSize.S_1024_BERT; //null: no blocking
    private int maxMessageSize;
    private int maxIncomingBlockTransferSize;


    public CoapTcpMessaging(CoapTransport coapTransport) {
        this(coapTransport, new CoapTcpCSMStorageImpl());
    }

    public CoapTcpMessaging(CoapTransport coapTransport, CoapTcpCSMStorage csmStorage) {
        super(coapTransport);
        this.csmStorage = csmStorage;
    }


    public void setLocalBlockSize(BlockSize blockSize) {
        this.blockSize = blockSize;
    }

    public void setLocalMaxMessageSize(int maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    @Override
    public BlockSize getLocalBlockSize() {
        return blockSize;
    }

    @Override
    public int getLocalMaxMessageSize() {
        return maxMessageSize;
    }

    @Override
    public int getMaxIncomingBlockTransferSize() {
        return maxIncomingBlockTransferSize;
    }

    public void setMaxIncomingBlockTransferSize(int maxIncomingBlockTransferSize) {
        this.maxIncomingBlockTransferSize = maxIncomingBlockTransferSize;
    }

    @Override
    public void ping(InetSocketAddress destination, Callback<CoapPacket> callback) {
        CoapPacket pingRequest = new CoapPacket(Code.C702_PING, null, destination);
        makeRequest(pingRequest, callback, TransportContext.NULL);
    }

    @Override
    protected boolean handlePing(CoapPacket packet) {
        if (packet.getCode() == null && packet.getMethod() == null) {
            LOGGER.debug("CoAP ping received.");
            // ignoring normal CoAP ping, according to https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.4
            return true;
        }

        return handleSignal(packet);
    }

    private boolean handleSignal(CoapPacket packet) {
        if (packet.getCode() == null || !packet.getCode().isSignaling()) {
            return false;
        }

        if (packet.getCode() == Code.C701_CSM) {
            SignalingOptions signalingOpts = packet.headers().toSignallingOptions(packet.getCode());
            Long maxMessageSize = signalingOpts.getMaxMessageSize();
            Boolean blockWiseTransferBERT = signalingOpts.getBlockWiseTransfer();
            CoapTcpCSM current = csmStorage.getOrDefault(packet.getRemoteAddress());
            csmStorage.updateCapability(packet.getRemoteAddress(), current.withNewOptions(maxMessageSize, blockWiseTransferBERT));
        } else if (packet.getCode() == Code.C702_PING) {
            CoapPacket pongResp = new CoapPacket(Code.C703_PONG, MessageType.Acknowledgement, packet.getRemoteAddress());
            pongResp.setToken(packet.getToken());
            sendPacket(pongResp, packet.getRemoteAddress(), TransportContext.NULL);
        } else if (packet.getCode() == Code.C703_PONG) {
            //handle this as reply
            return false;
        } else if (packet.getCode() == Code.C705_ABORT) {
            onDisconnected(packet.getRemoteAddress());

        } else {
            LOGGER.debug("[{}] Ignored signal message: {}", packet.getRemoteAddress(), packet.getCode());
        }

        return true;
    }

    @Override
    public void makeRequest(CoapPacket packet, Callback<CoapPacket> callback, TransportContext transContext) {

        int payloadLen = packet.getPayload().length;
        int maxMessageSize = getMaxMessageSize(packet.getRemoteAddress());

        if (payloadLen > maxMessageSize && !isBlockTransferSupported(packet.getRemoteAddress())) {
            callback.callException(
                    new CoapException("Request payload size is too big and no block transfer support is enabled for " + packet.getRemoteAddress() + ": " + payloadLen)
            );
            return;
        }

        RequestCallback requestCallback = wrapCallback(callback);

        DelayedTransactionId transId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        transactions.put(transId, requestCallback);


        sendPacket(packet, packet.getRemoteAddress(), transContext)
                .whenComplete((wasSent, maybeError) -> {
                    if (maybeError == null) {
                        requestCallback.onSent();
                    } else {
                        removeTransactionExceptionally(transId, (Exception) maybeError);
                    }
                });
    }

    @Override
    public void sendResponse(CoapPacket request, CoapPacket response, TransportContext transContext) {
        sendPacket(response, response.getRemoteAddress(), transContext);
    }

    private boolean isBlockTransferSupported(InetSocketAddress address) {
        return csmStorage.getOrDefault(address).isBlockTransferEnabled();
    }

    @Override
    protected boolean handleDelayedResponse(CoapPacket packet) {
        return false;
    }

    @Override
    protected boolean handleResponse(CoapPacket packet) {
        DelayedTransactionId transId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        RequestCallback callback = transactions.remove(transId);

        if (callback != null) {
            callback.call(packet);
            return true;
        }

        return false;
    }

    @Override
    public void onConnected(InetSocketAddress remoteAddress) {
        CoapPacket packet = new CoapPacket(remoteAddress);
        packet.setCode(Code.C701_CSM);

        SignalingOptions signalingOpts = new SignalingOptions();
        signalingOpts.setMaxMessageSize(getLocalMaxMessageSize());
        signalingOpts.setBlockWiseTransfer(getLocalBlockSize() != null);

        packet.headers().putSignallingOptions(signalingOpts);

        coapTransporter.sendPacket(packet, remoteAddress, TransportContext.NULL);
    }

    @Override
    public void onDisconnected(InetSocketAddress remoteAddress) {
        csmStorage.remove(remoteAddress);

        Set<DelayedTransactionId> trans = transactions.keySet();
        for (DelayedTransactionId transId : trans) {
            if (transId.hasRemoteAddress(remoteAddress)) {
                removeTransactionExceptionally(transId, new IOException("Socket closed"));
            }
        }

        LOGGER.info("[{}] Disconnected", remoteAddress);
    }

    private void removeTransactionExceptionally(DelayedTransactionId transId, Exception error) {
        RequestCallback requestCallback = transactions.remove(transId);
        if (requestCallback != null) {
            requestCallback.callException(error);
        }
    }

    @Override
    public BlockSize getBlockSize(InetSocketAddress remoteAddress) {
        CoapTcpCSM capabilities = csmStorage.getOrDefault(remoteAddress);

        if (capabilities.isBERTEnabled()) {
            return BlockSize.S_1024_BERT;
        }

        if (capabilities.isBlockTransferEnabled()) {
            long maxMessageSize = capabilities.getMaxMessageSize();

            for (BlockSize blockSize : REVERSED_BLOCK_SIZES) {
                if (blockSize.getSize() < maxMessageSize) {
                    return blockSize;
                }
            }
        }
        return null; // no block transfers enabled for connection
    }

    @Override
    public int getMaxMessageSize(InetSocketAddress address) {
        CoapTcpCSM capabilities = csmStorage.getOrDefault(address);
        return capabilities.getMaxMessageSizeInt();
    }

    @Override
    protected void stop0() {
        // no additional stop hooks
    }

    @Override
    public void makePrioritisedRequest(CoapPacket packet, Callback<CoapPacket> callback, TransportContext transContext) {
        //not applicable in TCP transport
        makeRequest(packet, callback, transContext);
    }

}