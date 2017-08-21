import java.nio.ByteBuffer;
import java.util.Random;

public class Query {

    char queryID;
    byte QR;
    byte OPCODE;
    byte AA;
    byte TC;
    byte RD;

    byte RA;
    byte Z;
    byte RCode;

    char DQCount;
    char ANCount;
    char NACount;
    char ARCount;

    byte[] QNAME;
    char QTYPE;
    char QCLASS;


    public Query(String fqdn, boolean IPV6Query) {
        //set query
        this.queryID = getID();
        this.QR = 0b00;
        this.OPCODE = 0b00;
        this.AA = 0b00;
        this.TC = 0b00;
        this.RD = 0b00;

        this.RA = 0b00;
        this.Z = 0b00;
        this.RCode = 0b00;

        this.DQCount = 0b01;
        this.ANCount = 0b00;
        this.NACount = 0b00;
        this.ARCount = 0b00;

        this.QNAME = formByteQuestion(fqdn);
        this.QTYPE = (char) ((IPV6Query) ? 0x1C : 0x01);

        this.QCLASS = 0b01;
    }

    public byte[] encode() {
        byte tempByte1 = (byte) ((QR << 7) | (OPCODE << 6) | (AA << 2) | (TC << 1) | RD);
        byte tempByte2 = (byte) ((RA << 7) | (Z << 4) | RCode);

        // assume there is only one question. Adding all the fields to byteBuffer
        ByteBuffer result = ByteBuffer.allocate(12 + QNAME.length + 4);
        result.putChar(queryID);
        result.put(tempByte1);
        result.put(tempByte2);
        result.putChar(DQCount);
        result.putChar(ANCount);
        result.putChar(NACount);
        result.putChar(ARCount);
        result.put(QNAME);
        result.putChar(QTYPE);
        result.putChar(QCLASS);

        return result.array();
    }

    //Convert the string fqdn to QNAME in byte array
    private static byte[] formByteQuestion(String fqdn) {
        String[] splitURL = fqdn.split("\\.");

        ByteBuffer charbuf = ByteBuffer.allocate(fqdn.length() + 2);
        for (String aSplitURL : splitURL) {
            charbuf.put((byte) aSplitURL.length());
            charbuf.put(aSplitURL.getBytes());
        }
        charbuf.put((byte) 0x00);

        return charbuf.array();
    }

    // randomly generate queryID
    private char getID() {
        Random rand = new Random(System.currentTimeMillis());
        return (char) (rand.nextInt(0x00FFFF));
    }

    public char getQueryID() {
        return queryID;
    }
}

