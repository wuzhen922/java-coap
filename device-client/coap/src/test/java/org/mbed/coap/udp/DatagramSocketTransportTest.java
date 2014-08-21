package org.mbed.coap.udp;

import org.mbed.coap.client.CoapClient;
import org.mbed.coap.test.StubCoapServer;
import org.mbed.coap.transport.TransportContext;
import org.mbed.coap.transport.TransportReceiver;
import org.mbed.coap.udp.DatagramSocketTransport;
import org.mbed.coap.udp.QoSDatagramSocket;
import org.mbed.coap.udp.TrafficClassTransportContext;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import static org.junit.Assert.*;
import org.junit.Test;
import static org.mockito.Mockito.*;

/**
 *
 * @author szymon
 */
public class DatagramSocketTransportTest {

    @Test
    public void clientServerTest() throws Exception {
        StubCoapServer server = new StubCoapServer(new DatagramSocketTransport(new InetSocketAddress(0)));
        server.start();

        CoapClient client = CoapClient.newBuilder(server.getAddress()).transport(new DatagramSocketTransport(0)).build();

        assertNotNull(client.ping().get());
        server.stop();
    }

    @Test
    public void initializingWithStateException() throws IOException {
        DatagramSocketTransport trans = new DatagramSocketTransport(0);
        try {
            try {
                trans.send("test".getBytes(), 4, new InetSocketAddress(5683), TransportContext.NULL);
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof IllegalStateException);
            }

            trans.start(mock(TransportReceiver.class));

            try {
                trans.setReuseAddress(true);
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof IllegalStateException);
            }
            try {
                trans.setSocketBufferSize(1234);
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof IllegalStateException);
            }
        } finally {
            trans.stop();
        }
    }

    @Test
    public void initializeWithParameters() throws IOException {
        DatagramSocketTransport trans = new DatagramSocketTransport(0);
        trans.setReuseAddress(false);
        trans.setSocketBufferSize(12345);
        trans.start(mock(TransportReceiver.class));

        assertTrue(trans.getSocket().isBound());
        assertFalse(trans.getSocket().isClosed());
        assertEquals(12345, trans.getSocket().getSendBufferSize());
        assertFalse(trans.getSocket().getReuseAddress());

        trans.stop();
        assertTrue(trans.getSocket().isClosed());
    }

    @Test
    public void reopenSamePort() throws IOException {
        DatagramSocketTransport trans = new DatagramSocketTransport(0);
        trans.start(mock(TransportReceiver.class));
        assertFalse(trans.getSocket().isClosed());
        int localPort = trans.getLocalSocketAddress().getPort();
        trans.stop();
        assertTrue(trans.getSocket().isClosed());

        //bind again to same port
        trans = new DatagramSocketTransport(localPort);

        trans.start(mock(TransportReceiver.class));
        assertFalse(trans.getSocket().isClosed());
        System.out.println(trans.getLocalSocketAddress());
        trans.stop();
    }

    @Test
    public void sendingWithTrafficClass() throws Exception {
        final DatagramSocket socket = spy(new QoSDatagramSocket(new InetSocketAddress(0)));
        DatagramSocketTransport trans = spy(new DatagramSocketTransport(0));
        when(trans.createSocket()).thenReturn(socket);

        trans.start(mock(TransportReceiver.class));

        trans.send("dupa".getBytes(), 4, new InetSocketAddress("::1", 5683), TrafficClassTransportContext.height());
        verify(socket).setTrafficClass(TrafficClassTransportContext.HIGH);
        verify(socket).setTrafficClass(0);

        reset(socket);
        trans.send("dupa".getBytes(), 4, new InetSocketAddress("::1", 5683), new TrafficClassTransportContext(89, TransportContext.NULL));
        verify(socket).setTrafficClass(89);
        verify(socket).setTrafficClass(0);

        //no traffic class
        reset(socket);
        trans.send("dupa".getBytes(), 4, new InetSocketAddress("::1", 5683), null);
        verify(socket, never()).setTrafficClass(anyInt());

        trans.stop();

    }

    @Test
    public void sendingCoapWithTrafficClass() throws Exception {
        final DatagramSocket socket = spy(new QoSDatagramSocket(new InetSocketAddress(0)));
        DatagramSocketTransport trans = spy(new DatagramSocketTransport(0));
        when(trans.createSocket()).thenReturn(socket);

        CoapClient client = CoapClient.newBuilder(5683).transport(trans).timeout(10000).build();

        client.resource("/test").context(TrafficClassTransportContext.height()).get();
        verify(socket).setTrafficClass(TrafficClassTransportContext.HIGH);
        verify(socket).setTrafficClass(0);

        reset(socket);
        client.resource("/test").get();
        verify(socket, never()).setTrafficClass(anyInt());

        client.close();
    }
}
