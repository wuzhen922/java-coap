package org.mbed.coap;

import org.mbed.coap.test.utils.Utils;
import org.mbed.coap.BlockOption;
import org.mbed.coap.BlockSize;
import org.mbed.coap.CoapPacket;
import org.mbed.coap.Code;
import org.mbed.coap.HeaderOptions;
import org.mbed.coap.MessageType;
import org.mbed.coap.Method;
import org.mbed.coap.exception.CoapException;
import org.mbed.coap.linkformat.LinkFormat;
import org.mbed.coap.linkformat.LinkFormatBuilder;
import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 *
 * @author szymon
 */
public class CoapPackeTest {

    @Test
    public void linkFormat() throws ParseException {

        String linkFormatString = "</a/relay>;if=\"ns.wadl#a\";rt=\"ns:relay\";ct=\"0\"";
        LinkFormat[] lf = LinkFormatBuilder.parseList(linkFormatString);
        assertEquals(1, lf.length);
        assertEquals("/a/relay", lf[0].getUri());
        assertArrayEquals(new String[]{"ns.wadl#a"}, lf[0].getInterfaceDescriptionArray());
        assertArrayEquals(new String[]{"ns:relay"}, lf[0].getResourceTypeArray());
        assertEquals((Short) (short) 0, lf[0].getContentType());

        linkFormatString = "</a/relay>;if=\"ns.wadl#a\";rt=\"ns:relay\";ct=\"0\","
                + "</s/light>;if=\"ns.wadl#s\";rt=\"ucum:lx\";ct=\"0\","
                + "</s/power>;if=\"ns.wadl#s\";rt=\"ucum:W\";ct=\"0\","
                + "</s/temp>;if=\"ns.wadl#s\";rt=\"ucum:Cel\";ct=\"0\"";

        lf = LinkFormatBuilder.parseList(linkFormatString);
        assertEquals(4, lf.length);
    }

    @Test
    public void coapPacketTest1() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Confirmable, "/test", null);
        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(new ByteArrayInputStream(rawCp));

        assertArrayEquals(rawCp, CoapPacket.serialize(cp2));
        assertEquals(Method.GET, cp2.getMethod());
        assertEquals(MessageType.Confirmable, cp2.getMessageType());
        assertEquals("/test", cp2.headers().getUriPath());
        assertNull(cp2.getCode());
        assertNull(cp2.getPayloadString());
        assertEquals(1, cp2.getVersion());
    }

    @Test
    public void coapPacketTest2() throws CoapException {
        CoapPacket cp = new CoapPacket(Code.C205_CONTENT, MessageType.Acknowledgement, null);
        cp.setPayload("TEST");

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.read(rawCp);

        assertArrayEquals(rawCp, CoapPacket.serialize(cp2));
        assertCoapPackets(cp, cp2);
    }

    @Test
    public void coapPacketTest3() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.PUT, MessageType.Confirmable, "", null);
        cp.setMessageId(1234);
        cp.headers().setUriPath("/test2");
        cp.headers().setLocationPath("");
        cp.headers().setAccept(new short[]{});
        cp.setPayload("t�m� on varsin miel??$�");
        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(cp2);
        assertArrayEquals(rawCp, CoapPacket.serialize(cp2));
        assertEquals(Method.PUT, cp2.getMethod());
        assertEquals(MessageType.Confirmable, cp2.getMessageType());
        assertEquals("/test2", cp2.headers().getUriPath());
    }

    @Test
    public void coapPacketTestWithHightNumberBlock() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.PUT, MessageType.Reset, "", null);
        cp.headers().setBlock2Res(new BlockOption(0, BlockSize.S_16, true));
        cp.setMessageId(0xFFFF);

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(new ByteArrayInputStream(rawCp));
        System.out.println(cp);
        System.out.println(cp2);

        assertCoapPackets(cp, cp2);
    }

    @Test
    public void coapPacketTestWithPathAndQuery() throws CoapException, ParseException {
        CoapPacket cp = new CoapPacket(Method.DELETE, MessageType.NonConfirmable, null, null);
        cp.headers().setUriPath("/test/path/1");
        cp.headers().setUriQuery("par1=1&par2=201");
        cp.headers().setLocationPath("/loc/path/2");
        cp.headers().setLocationQuery("lpar1=1&lpar2=2");
        cp.setMessageId(3612);

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(Arrays.toString(cp.toByteArray()));
        System.out.println(cp2);
        System.out.println(Arrays.toString(cp2.toByteArray()));
        assertCoapPackets(cp, cp2);

        Map<String, String> q = new HashMap<String, String>();
        q.put("par1", "1");
        q.put("par2", "201");
        assertEquals(q, cp.headers().getUriQueryMap());

        assertNull(cp.headers().getContentFormat());
        assertNull(cp.headers().getContentFormat());

    }

    @Test
    public void coapPacketTestWithHeaders() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.DELETE, MessageType.NonConfirmable, null, null);
        cp.headers().setAccept(new short[]{12, 432});
        cp.headers().setIfMatch(new byte[][]{{(byte) 98, (byte) 53}});
        cp.headers().setIfNonMatch(Boolean.TRUE);
        cp.headers().setContentFormat((short) 423);
        cp.headers().setEtag(new byte[][]{Utils.intToByteArray(98), Utils.intToByteArray(78543)});
        cp.headers().setMaxAge(7118543L);
        cp.headers().setObserve(123);
        cp.headers().setProxyUri("/proxy/uri/test");
        cp.headers().setUriPort(64154);

        cp.setMessageId(3612);

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(Arrays.toString(cp.toByteArray()));
        System.out.println(cp2);
        System.out.println(Arrays.toString(cp2.toByteArray()));
        assertCoapPackets(cp, cp2);
    }

    private static void assertCoapPackets(CoapPacket cp1, CoapPacket cp2) {
        assertEquals(cp1.getMethod(), cp2.getMethod());
        assertEquals(cp1.getMessageType(), cp2.getMessageType());
        assertEquals(cp1.getCode(), cp2.getCode());
        assertEquals(cp1.getMessageId(), cp2.getMessageId());

        assertEquals(cp1.headers().getBlock1Req(), cp2.headers().getBlock1Req());
        assertEquals(cp1.headers().getBlock2Res(), cp2.headers().getBlock2Res());
        assertEquals(cp1.headers().getUriPath(), cp2.headers().getUriPath());
        assertEquals(cp1.headers().getUriAuthority(), cp2.headers().getUriAuthority());
        assertEquals(cp1.headers().getUriHost(), cp2.headers().getUriHost());
        assertEquals(cp1.headers().getUriQuery(), cp2.headers().getUriQuery());
        assertEquals(cp1.headers().getLocationPath(), cp2.headers().getLocationPath());
        assertEquals(cp1.headers().getLocationQuery(), cp2.headers().getLocationQuery());

        assertArrayEquals(cp1.headers().getAccept(), cp2.headers().getAccept());
        assertArrayEquals(cp1.headers().getIfMatch(), cp2.headers().getIfMatch());
        assertArrayEquals(cp1.headers().getEtagArray(), cp2.headers().getEtagArray());

        assertEquals(cp1.headers().getIfNonMatch(), cp2.headers().getIfNonMatch());
        assertEquals(cp1.headers().getContentFormat(), cp2.headers().getContentFormat());
        assertArrayEquals(cp1.headers().getEtag(), cp2.headers().getEtag());
        assertEquals(cp1.headers().getMaxAge(), cp2.headers().getMaxAge());
        assertEquals(cp1.headers().getObserve(), cp2.headers().getObserve());
        assertEquals(cp1.headers().getProxyUri(), cp2.headers().getProxyUri());
        assertArrayEquals(cp1.getToken(), cp2.getToken());
        assertEquals(cp1.headers().getUriPort(), cp2.headers().getUriPort());

        assertEquals(cp1.getPayloadString(), cp2.getPayloadString());
        assertEquals(1, cp2.getVersion());

    }

    @Test
    public void coapPacketTestWithEmptyLocHeader() throws CoapException {
        CoapPacket cp = new CoapPacket(Method.GET, MessageType.Reset, "", null);
        cp.headers().setBlock2Res(new BlockOption(0, BlockSize.S_16, true));
        cp.headers().setLocationQuery("");
        cp.setMessageId(0);

        byte[] rawCp = CoapPacket.serialize(cp);
        CoapPacket cp2 = CoapPacket.deserialize(new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(Arrays.toString(cp.toByteArray()));
        System.out.println(cp2);
        System.out.println(Arrays.toString(cp2.toByteArray()));

        assertEquals(Method.GET, cp2.getMethod());
        assertEquals(MessageType.Reset, cp2.getMessageType());
        assertEquals(cp.headers().getBlock2Res(), cp2.headers().getBlock2Res());
        assertEquals(null, cp2.headers().getUriPath());
        assertNull(cp2.getCode());
        assertNull(cp2.getPayloadString());
        assertEquals(1, cp2.getVersion());
    }

    @Test(expected = org.mbed.coap.exception.CoapException.class)
    public void versionTest() throws CoapException {
        CoapPacket.read(new byte[]{(byte) 0x85});
    }

    @Test
    public void testParseUriQuery() throws ParseException {
        Map<String, String> q = new HashMap<String, String>();
        q.put("par1", "12");

        assertEquals(q, HeaderOptions.parseUriQuery("par1=12"));
        assertEquals(q, HeaderOptions.parseUriQuery("?par1=12"));

        q.put("par2", "14");
        assertEquals(q, HeaderOptions.parseUriQuery("par1=12&par2=14"));
        assertEquals(q, HeaderOptions.parseUriQuery("?par1=12&par2=14"));

        q.put("d", "b");
        assertEquals(q, HeaderOptions.parseUriQuery("par1=12&par2=14&d=b"));
    }

    @Test
    public void unknownHeaderTest() throws CoapException {
        CoapPacket cp = new CoapPacket();
        byte[] hdrVal = new byte[]{1, 2, 3, 4, 5, 6, 7};
        int hdrType = 100;
        cp.headers().put(hdrType, hdrVal);
        assertArrayEquals(hdrVal, cp.headers().getCustomOption(hdrType));

        byte[] rawCp = CoapPacket.serialize(cp);

        CoapPacket cp2 = CoapPacket.deserialize(new ByteArrayInputStream(rawCp));
        System.out.println(cp);
        System.out.println(cp2);
        //assertEquals(1, cp2.headers().getUnrecognizedOptions().size());
        assertArrayEquals(hdrVal, cp2.headers().getCustomOption(hdrType));
        assertEquals(cp.headers(), cp2.headers());
    }

    @Test
    public void uriPathWithDoubleSlashes() throws CoapException {
        CoapPacket cp = new CoapPacket();
        cp.headers().setUriPath("/3/13//");
        cp.headers().setLocationPath("/2//1");
        cp.headers().setUriQuery("te=12&&ble=14");
        cp.toByteArray();
        
        CoapPacket cp2 = CoapPacket.read(cp.toByteArray());
        assertEquals(cp, cp2);
        assertEquals("/3/13//", cp2.headers().getUriPath());
        assertEquals("/2//1", cp2.headers().getLocationPath());
    }
}
