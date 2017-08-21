
public class Section {
    protected class Qname {
        String name;
        int length;

        Qname(String a, int b) {
            name = a;
            length = b;
        }
    }

    public String name;

    public int rrtype;
    public int rrclass;
    public int length;
    protected int curoff = 0;
    public int part = 0;

    public Section(byte[] data, int offset, int part) throws Exception {
        Qname nawa = getQname(data, offset);
        this.part = part;
        this.name = nawa.name;
        curoff = offset + nawa.length;

        this.rrtype = byteToInt(data, curoff);
        curoff += 2;
        this.rrclass = byteToInt(data, curoff);
        curoff += 2;

        this.length = curoff - offset;
    }

    public Qname getQname(byte[] data, int offset) throws Exception {
        String name = "";
        byte curByte = data[offset];
        int format = curByte & 0xC0;
        int path = 0;   // path represents the length of current contiguous sections,
                        // as opposed to name length which includes section before the start of the name
        while (true) {
            path++;     // we read a byte before the current loop
            if (format == 0) {
                //label format
                int length = curByte & 0x3F;
                if (length == 0) return new Qname(name, path); // we have reached an end
                for (int i = 0; i < length; i++) name += (char) data[offset + path + i]; // consume bytes
                path += length;
                curByte = data[offset + path];
                format = curByte & 0xC0;
                if (curByte != 0) name += '.'; //not the end but in between
            } else if (format == 0xC0) {
                //pointer format
                return new Qname(name + getQname(data, byteToInt(data, offset + path - 1) & 0x3FFF).name, path + 1);
            } else {
                throw new Exception("QNAME has invalid format! Name is currently " + name + ", format is " + format);
            }
        }
    }

    public void trace() {
        System.out.format("       %-30s %-4s ", name, rrtype);
        switch (rrclass)
        {
            case 1:
                System.out.println("IN");
                break;
            case 2:
                System.out.println("Unassigned");
                break;
            case 3:
                System.out.println("CH");
                break;
            case 4:
                System.out.println("HS");
                break;
            case 254:
                System.out.println("QCLASS NONE");
                break;
            case 255:
                System.out.println("ANY");
                break;
            default:
                System.out.println("Reserved/Other");
        }
    }

    protected int byteToInt(byte[] array, int offset) { //adapted from Ribo's answer at https://stackoverflow.com/questions/4768933/read-two-bytes-into-an-integer
        return array[offset] << 8 & 0xFF00 | array[offset + 1] & 0x00FF;
    }
}