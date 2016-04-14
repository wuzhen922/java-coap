/*
 * Copyright (C) 2011-2016 ARM Limited. All rights reserved.
 */
package protocolTests;

import static org.testng.Assert.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.client.CoapClientBuilder;
import org.mbed.coap.packet.BlockSize;
import org.mbed.coap.packet.CoapPacket;
import org.mbed.coap.packet.Code;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.server.MessageIdSupplierImpl;
import org.mbed.coap.transmission.SingleTimeout;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import protocolTests.utils.CurrentThreadExecutor;
import protocolTests.utils.TransportConnectorMock;

/**
 * Created by szymon.
 */
public class SeparateResponseTest {
    private static final InetSocketAddress SERVER_ADDRESS = new InetSocketAddress("127.0.0.1", 5683);
    private TransportConnectorMock transport;
    private CoapClient client;

    @BeforeMethod
    public void setUp() throws Exception {
        transport = new TransportConnectorMock();

        CoapServer coapServer = CoapServer.builder().transport(transport).midSupplier(new MessageIdSupplierImpl(0)).blockSize(BlockSize.S_32)
                .executor(new CurrentThreadExecutor())
                .timeout(new SingleTimeout(500)).build();
        coapServer.start();

        client = CoapClientBuilder.clientFor(SERVER_ADDRESS, coapServer);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        client.close();
    }

    @Test
    public void shouldResponseWithEmptyAckAndSeparateResponse() throws Exception {
        //empty ack
        transport.when(newCoapPacket(1).token(123).get().uriPath("/path1").build())
                .then(newCoapPacket(1).ack(null).build());

        CompletableFuture<CoapPacket> futResp = client.resource("/path1").token(123).get();

        //separate response
        transport.receive(newCoapPacket(2).token(123).non(Code.C205_CONTENT).payload("dupa").build(), SERVER_ADDRESS);

        assertEquals("dupa", futResp.get().getPayloadString());
    }

    @Test
    public void shouldResponseWithSeparateResponse_withoutEmptyAck() throws Exception {
        CompletableFuture<CoapPacket> futResp = client.resource("/path1").token(123).get();

        //separate response, no empty ack
        transport.receive(newCoapPacket(2).token(123).con(Code.C205_CONTENT).payload("dupa").build(), SERVER_ADDRESS);
        assertEquals("dupa", futResp.get().getPayloadString());
    }
}