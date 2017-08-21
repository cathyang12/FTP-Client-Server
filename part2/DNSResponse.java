
import java.net.InetAddress;
import java.util.*;


// Lots of the action associated with handling a DNS query is processing
// the response. Although not required you might find the following skeleton of
// a DNSreponse helpful. The class below has bunch of instance data that typically needs to be
// parsed from the response. If you decide to use this class keep in mind that it is just a
// suggestion and feel free to add or delete methods to better suit your implementation as
// well as instance variables.


class DNSResponse {
    public int queryID;                     // this is for the response it must match the one in the request
    public boolean qr;                      // is this a query or a response
    public int opcode;                      // what kind of query?
    public int answerCount = 0;             // number of answers
    public int qdcount;                     // number of entries in the question section
    public boolean decoded = false;         // Was this response successfully decoded? false means all other fields are indeterminate and invalid
    public int nsCount = 0;                 // number of nscount response records
    public int additionalCount = 0;         // number of additional (alternate) response records

    public boolean authoritative = false;   // Is this an authoritative record
    public boolean tc;                      // truncated response?
    public boolean rd;                      // recursion desired?
    public boolean ra;                      // recursion available?
    public int rcode;                       // response code, 0 == OK, not 0 == not OK
    public ArrayList<Section> que = new ArrayList<>();                      // the Question(s)
    public ArrayList<ResourceRecord> ans = new ArrayList<>();               // answers, if any
    public ArrayList<ResourceRecord> ns = new ArrayList<>();                // nameservers to query further
    public ArrayList<ResourceRecord> add = new ArrayList<>();               // additional sections
    // Note you will almost certainly need some additional instance variables.

    // When in trace mode you probably want to dump out all the relevant information in a response
    void trace() {
        System.out.println("Response ID: " + this.queryID + " Authoritative = " + ((this.authoritative) ? "true" : "false"));
        /*System.out.println("    Question Section");
        for (Section rr : this.que) {
            rr.trace();
        }*/
        System.out.println("    Answers (" + this.answerCount + ")");
        for (ResourceRecord rr : this.ans) {
            rr.trace();
        }
        System.out.println("    Nameservers (" + this.nsCount + ")");
        for (ResourceRecord rr : this.ns) {
            rr.trace();
        }
        System.out.println("    Additional Information (" + this.additionalCount + ")");
        for (ResourceRecord rr : this.add) {
            rr.trace();
        }
    }

    private int byteToInt(byte[] array, int offset) { //adapted from Ribo's answer at https://stackoverflow.com/questions/4768933/read-two-bytes-into-an-integer
        return array[offset] << 8 & 0xFF00 | array[offset + 1] & 0x00FF;
    }

    // The constructor: you may want to add additional parameters, but the two shown are
    // probably the minimum that you need.

    DNSResponse(byte[] data, int len, boolean ipv6) {
        try {
            if(len < 12) throw new Exception("Corrupted packet received!"); //the smallest valid DNS response must have a header
            int currentOffset;
            // The following are probably some of the things
            // you will need to do.
            // Extract the query ID
            this.queryID = byteToInt(data, 0);

            // Make sure the message is a query response and determine
            // if it is an authoritative response or note
            this.qr = (data[2] & 0b10000000) == 0b10000000;
            if (!this.qr) {
                //not response, throw error or something
                this.decoded = false;
                throw new Exception("Packet is not actually a DNS response!");
            }

            this.opcode = data[2] & 0b01111000;
            this.authoritative = ((data[2] & 0b00000100) == 0b00000100);
            this.tc = ((data[2] & 0b00000010) == 0b00000010);
            this.rd = ((data[2] & 0b00000001) == 0b00000001);
            this.ra = ((data[3] & 0b10000000) == 0b10000000);
            this.rcode = data[3] & 0b00001111;

            this.qdcount = byteToInt(data, 4);
            // determine answer count
            this.answerCount = byteToInt(data, 6);
            // determine NS Count
            this.nsCount = byteToInt(data, 8);
            // determine additional record count
            this.additionalCount = byteToInt(data, 10);

            // we have parsed the header, now we parse the resource records
            // Extract list of answers, name server, and additional information response
            // records

            currentOffset = 12;
            for (int i = 0; i < this.qdcount; i++) {
                Section nrr = new Section(data, currentOffset, 0);
                this.que.add(nrr);
                currentOffset += nrr.length;
            }
            for (int i = 0; i < this.answerCount; i++) {
                ResourceRecord nrr = new ResourceRecord(data, currentOffset, 1);
                if (!nrr.unknownHost) {
                    this.ans.add(nrr);
                    if (!this.authoritative) {
                        if (ipv6) {
                            if (nrr.rrclass == 0x1C) this.authoritative = true;
                        } else {
                            if (nrr.rrclass == 0x1) this.authoritative = true;
                        }
                    }
                }
                currentOffset += nrr.length;
            }
            for (int i = 0; i < this.nsCount; i++) {
                ResourceRecord nrr = new ResourceRecord(data, currentOffset, 2);
                if (!nrr.unknownHost) this.ns.add(nrr);
                currentOffset += nrr.length;
            }
            for (int i = 0; i < this.additionalCount; i++) {
                ResourceRecord nrr = new ResourceRecord(data, currentOffset, 3);
                this.add.add(nrr);
                currentOffset += nrr.length;
            }
            this.decoded = true;
        } catch (Exception e) {
            this.decoded = false;
            //System.out.println(e.getLocalizedMessage());
            //System.out.println(Arrays.toString(e.getStackTrace()));
        }
    }

    ResourceRecord getNS() {
        if (this.nsCount == 0) {
            return null;
        } else {
            ResourceRecord root = null;

            // If the addition section empty, return the first Name server
            if (this.add.size() == 0) {
                ResourceRecord result = this.ns.get(0);
                if (result.rrtype == 2) return this.ns.get(0);
            }

            for (ResourceRecord xx : this.add) {
                for (ResourceRecord rr : this.ns) {
                    if (rr.rrtype == 2) {
                        if (rr.rdata.getHostName().equals(xx.name)) {
                            return xx;
                        }
                        root = rr;
                    }
                }
            }
            return root;
        }
    }

    // You will probably want a methods to extract a compressed FQDN, IP address
    // cname, authoritative DNS servers and other values like the query ID etc.


    // You will also want methods to extract the response records and record
    // the important values they are returning. Note that an IPV6 reponse record
    // is of type 28. It probably wouldn't hurt to have a response record class to hold
    // these records.

}