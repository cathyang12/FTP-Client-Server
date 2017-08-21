import java.io.IOException;
import java.net.*;

/**
 * @author Donald Acton
 *         This example is adapted from Kurose & Ross
 *         Feel free to modify and rearrange code as you see fit
 */
public class DNSlookup {

    static final int MIN_PERMITTED_ARGUMENT_COUNT = 2;
    static final int MAX_PERMITTED_ARGUMENT_COUNT = 3;
    static InetAddress rootServer;
    static int port = 53;
    static String orgFqdn;
    static boolean gotAns = false;

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception {
        int argCount = args.length;
        boolean tracingOn = false;
        boolean IPV6Query = false;

        if (argCount < MIN_PERMITTED_ARGUMENT_COUNT || argCount > MAX_PERMITTED_ARGUMENT_COUNT) {
            usage();
            return;
        }

        if (argCount == 3) {  // option provided
            switch (args[2]) {
                case "-t":
                    tracingOn = true;
                    break;
                case "-6":
                    IPV6Query = true;
                    break;
                case "-t6":
                    tracingOn = true;
                    IPV6Query = true;
                    break;
                default:  // option present but wasn't valid option
                    usage();
                    return;
            }
        }

        //Get the IP address of the root server
        rootServer = InetAddress.getByName(args[0]);

        // the fqdn that we want to lookup
        // note: fqdn means fully qualified domain name
        orgFqdn = args[1];
        port = 53;
        // maximum depth of queries that can be sent
        int chain = 30;
        // hmb we're going in
        lookup(orgFqdn, rootServer, tracingOn, IPV6Query, chain);
    }

    private static void lookup(String fqdn, InetAddress server, boolean tracingOn, boolean IPV6Query, int chain) throws IOException {
        if (chain == 0) {   //too many lookups
            System.out.format("%-30s %-10d A %s\n", fqdn, -3, "0.0.0.0");
            return;
        }
        // establish new UDP connection
        DatagramSocket socket = new DatagramSocket();
        byte[] querybuf = new byte[512];
        byte[] responseBuf = new byte[1024];

        // new packet
        DatagramPacket uDPPacket = new DatagramPacket(querybuf, querybuf.length, server, port);

        // set query
        Query query = new Query(fqdn, IPV6Query);
        if (tracingOn) printQueryID(query.getQueryID(), fqdn, IPV6Query, server);
        uDPPacket.setData(query.encode());
        // can send udp the second time if the first time fails
        int limit = 2;
        while (true) {
            try {
                socket.setTrafficClass(0x04);
                socket.send(uDPPacket);
                socket.setSoTimeout(5000);
                break;
            } catch (SocketTimeoutException e) {
                limit--;
                if (limit == 0) {
                    System.out.format("%-30s %-10d %-4s %s\n", fqdn, -2, IPV6Query, "0.0.0.0");
                    return;
                }
            }
        }

        try {
            DatagramPacket receivePacket = new DatagramPacket(responseBuf, responseBuf.length);
            socket.receive(receivePacket);
            byte[] byteData = receivePacket.getData();

            DNSResponse res = new DNSResponse(byteData, byteData.length, IPV6Query);
            String recordType = (IPV6Query) ? "AAAA" : "A";

            //Any other errors that result in an address not being resolved, response is not decoded
            if (!res.decoded) {
                System.out.format("%-30s %-10d %-4s %s\n", fqdn, -4, recordType, "0.0.0.0");
                return;
            }

            if (tracingOn) res.trace();

            // has error if rcode !=0
            if (res.rcode > 0) {
                if (res.rcode == 3) {
                    System.out.format("%-30s %-10d %-4s %s\n", fqdn, -1, "A", "0.0.0.0");
                } else if (res.answerCount == 0) {
                    System.out.format("%-30s %-10d %-4s %s\n", fqdn, -6, "A", "0.0.0.0");
                } else {
                    System.out.format("%-30s %-10d %-4s %s\n", fqdn, -4, "A", "0.0.0.0");
                }
                return;
            }
            if (res.answerCount == 0) {
                if (res.nsCount == 0) {
                    // no answer and no name server provided
                    System.out.format("%-30s %-10d %-4s %s\n", fqdn, -4, "A", "0.0.0.0");
                } else {
                    ResourceRecord nameS = res.getNS();
                    if (nameS != null) {
                        lookup(fqdn, nameS.rdata, tracingOn, IPV6Query, chain - 1);
                    } else {
                        //SOA
                        System.out.format("%s %-5d %s %s\n", fqdn, -6, "A", "0.0.0.0");
                    }
                }
            } else {
                if (!gotAns) {
                    int recordT = (IPV6Query) ? 28 : 1;
                    for (ResourceRecord rr : res.ans) {
                        if (rr.rrtype == 0x5) {
                            //loop up CNAME fqdn
                            lookup(rr.rdata.getHostName(), rootServer, tracingOn, IPV6Query, chain - 1);
                        } else if (rr.rrtype == recordT) {
                            gotAns = true;
                            //print final IPAddress
                            System.out.format("%s %-1d %-3s %-1s\n", orgFqdn, rr.ttl, recordType, rr.rdata.getHostAddress());
                        }
                    }

                }
            }
        } catch (Exception e) {
            System.out.format("%-30s %-10d %-4s %s\n", fqdn, -4, "A", "0.0.0.0");
        }
    }

    static void printQueryID(char queryID, String fqdn, Boolean IPV6Query, InetAddress rootServer) {
        String addType = (IPV6Query) ? "AAAA" : "A";
        int intQuery = (int) queryID;
        System.out.println("\n\nQuery ID     " + intQuery + " " + fqdn + "  " + addType + " --> " + rootServer.getHostAddress());
    }

    private static void usage() {
        System.out.println("Usage: java -jar DNSlookup.jar rootDNS name [-6|-t|t6]");
        System.out.println("   where");
        System.out.println("       rootDNS - the IP address (in dotted form) of the root");
        System.out.println("                 DNS server you are to start your search at");
        System.out.println("       name    - fully qualified domain name to lookup");
        System.out.println("       -6      - return an IPV6 address");
        System.out.println("       -t      - trace the queries made and responses received");
        System.out.println("       -t6     - trace the queries made, responses received and return an IPV6 address");
    }
}
