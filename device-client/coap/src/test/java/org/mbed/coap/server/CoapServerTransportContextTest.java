package org.mbed.coap.server;

import org.mbed.coap.BlockSize;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.client.CoapClient;
import org.mbed.coap.exception.CoapCodeException;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.server.CoapExchange;
import org.mbed.coap.server.CoapServer;
import org.mbed.coap.test.InMemoryTransport;
import org.mbed.coap.test.SingleParamTransportContext;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.utils.CoapResource;
import java.io.IOException;
import java.net.InetSocketAddress;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

/**
 *
 * @author szymon
 */
public class CoapServerTransportContextTest {

    private CoapServer server;
    private final CoapResourceTest coapResourceTest = new CoapResourceTest();
    private final InMemoryTransport srvTransport = spy(new InMemoryTransport(5683));

    @Before
    public void setUp() throws IOException {
        server = CoapServer.newBuilder().transport(srvTransport).build();
        server.addRequestHandler("/test", coapResourceTest);
        server.start();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testRequest() throws IOException, CoapException {
        InMemoryTransport cliTransport = spy(new InMemoryTransport());
        CoapClient client = CoapClient.newBuilder(InMemoryTransport.createAddress(5683)).transport(cliTransport).build();

        srvTransport.setTransportContext(new TextTransportContext("dupa"));
        client.resource("/test").context(new TextTransportContext("client-sending")).sync().get();
        assertEquals("dupa", coapResourceTest.transportContext.get(MyEnum.TEXT, String.class));
        verify(cliTransport).send(isA(byte[].class), anyInt(), isA(InetSocketAddress.class), eq(new TextTransportContext("client-sending")));
        verify(srvTransport).send(isA(byte[].class), anyInt(), isA(InetSocketAddress.class), eq(new TextTransportContext("get-response")));

        srvTransport.setTransportContext(new TextTransportContext("dupa2"));
        client.resource("/test").sync().get();
        assertEquals("dupa2", coapResourceTest.transportContext.get(MyEnum.TEXT));

        client.close();
    }

    @Test
    public void testRequestWithBlocks() throws IOException, CoapException {
        InMemoryTransport cliTransport = spy(new InMemoryTransport());
        CoapClient client = CoapClient.newBuilder(InMemoryTransport.createAddress(5683)).transport(cliTransport).blockSize(BlockSize.S_16).build();

        srvTransport.setTransportContext(new TextTransportContext("dupa"));
        CoapPacket resp = client.resource("/test").payload("fhdkfhsdkj fhsdjkhfkjsdh fjkhs dkjhfsdjkh")
                .context(new TextTransportContext("client-block")).sync().put();

        assertEquals(Code.C201_CREATED, resp.getCode());
        assertEquals("dupa", coapResourceTest.transportContext.get(MyEnum.TEXT, String.class));

        //for each block it sends same transport context
        verify(cliTransport, times(3)).send(isA(byte[].class), anyInt(), isA(InetSocketAddress.class), eq(new TextTransportContext("client-block")));

        client.close();
    }

    private static class TextTransportContext extends SingleParamTransportContext {

        public TextTransportContext(String text) {
            super(MyEnum.TEXT, text, null);
        }

    }

    private static enum MyEnum {

        TEXT
    }

    private static class CoapResourceTest extends CoapResource {

        TransportContext transportContext;

        @Override
        public void get(CoapExchange exchange) throws CoapCodeException {
            transportContext = exchange.getRequestTransportContext();
            exchange.setResponseCode(Code.C205_CONTENT);
            exchange.setResponseTransportContext(new TextTransportContext("get-response"));
            exchange.sendResponse();
        }

        @Override
        public void put(CoapExchange exchange) throws CoapCodeException {
            transportContext = exchange.getRequestTransportContext();
            exchange.setResponseCode(Code.C201_CREATED);
            exchange.setResponseTransportContext(new TextTransportContext("put-response"));
            exchange.send();
        }

    }
}
