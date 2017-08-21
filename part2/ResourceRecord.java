import java.net.InetAddress;
import java.net.UnknownHostException;

public class ResourceRecord extends Section {
    public long ttl;
    public int rdlength;
    public InetAddress rdata;
    public boolean unknownHost = false;
    //some sort of rdata

    public ResourceRecord(byte[] data, int offset, int part) throws Exception {
        //extract a RR from the specified offset
        super(data, offset, part);

        this.ttl = (byteToInt(data, curoff) << 16) | byteToInt(data, curoff + 2);
        curoff += 4;
        this.rdlength = byteToInt(data, curoff);
        curoff += 2;

        //do rdata
        switch (this.rrtype) {
            case 0x1:
                //it's A record
                byte[] addr = new byte[4];
                System.arraycopy(data, curoff, addr, 0, 4);
                this.rdata = InetAddress.getByAddress(addr);
                break;
            case 0x2:
                //it's NS record
                try {
                    this.rdata = InetAddress.getByName(getQname(data, curoff).name);
                } catch (UnknownHostException e) {
                    this.unknownHost = true;
                }
                break;
            case 0x5:
                //it's CNAME record
                this.rdata = InetAddress.getByName(getQname(data, curoff).name);
                break;
            case 0x1C:
                //it's AAAA record
                addr = new byte[16];
                System.arraycopy(data, curoff, addr, 0, 16);
                this.rdata = InetAddress.getByAddress(addr);
                break;
            default:
                //something else
                this.rdata = null;
                break;
        }
        this.length = curoff - offset + this.rdlength;
    }

    public void trace() {
        String tip;
        switch (rrtype) {
            case 0x1:
                tip = "A";
                break;
            case 0x2:
                tip = "NS";
                break;
            case 0x5:
                tip = "CN";
                break;
            case 0x1C:
                tip = "AAAA";
                break;
            default:
                tip = Integer.toString(rrtype);
        }
        String addr = "----";
        if (part == 1 || part == 3) { //probably need addr
            if (rrtype == 5) {
                addr = rdata.getHostName();
            } else if (rrtype == 1 || rrtype == 2 || rrtype == 28) {
                addr = rdata.getHostAddress();
            }
        } else {
            if (rrtype == 1 || rrtype == 2 || rrtype == 5 || rrtype == 28) addr = rdata.getHostName();
        }
        System.out.format("       %-30s %-10d %-4s %s\n", name, ttl, tip, addr);
    }
}