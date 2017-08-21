
import java.lang.System;
import java.io.IOException;
import java.io.*;
import java.net.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSftp
{
    static final int ARG_CNT = 2;
    static String fromServer;
    static int IO_TIMEOUT=10000;
    static int TIMEOUT=20000;

    public static void main(String [] args) throws IOException //added this line
    {
        // If the number of arguments is not 2, exit the program.
        if (args.length != ARG_CNT) {
            System.out.print("Usage: cmd ServerAddress ServerPort\n");
            return;
        }

        String hostName = args[0];
        String subHostname;
        int portNumber = Integer.parseInt(args[1]);
        int subPortNumber;

        try {
            Socket kkSocket = new Socket(hostName, portNumber);
            kkSocket.setSoTimeout(TIMEOUT); //sets Socket Timeout for FFFC in milliseconds

            // from client to server
            PrintWriter out1 = new PrintWriter(kkSocket.getOutputStream(), true);
            BufferedWriter out=new BufferedWriter(out1);
            //from server to client
            BufferedReader in = new BufferedReader(new InputStreamReader(kkSocket.getInputStream()));
            //from user
            BufferedReader stdIn =new BufferedReader(new InputStreamReader(System.in));

            String fromUserString;
            fromServer = in.readLine();
            while (fromServer != null) {
                String[] fromUser = new String[2];
                System.out.print("csftp> ");
                String command = "";

                try {
                    fromUserString = stdIn.readLine();
                    fromUser = fromUserString.split(" ");
                    command = fromUser[0].toLowerCase().trim();

                }catch(IOException e){
                    printErrorMsg("FFFE", null, null);
                    System.exit(1);
                }

                switch (command) {
                    case "user":
                        if (fromUser.length!=2){
                            printErrorMsg("002", "", "");
                            break;
                        }
                        out.write("USER " + fromUser[1]+"\n");
                        out.flush();
                        System.out.println("--> USER " + fromUser[1]);
                        readMultiLine(in);
                        break;
                    case "pw":
                        if (fromUser.length!=2){
                            printErrorMsg("002", "", "");
                            break;
                        }
                        out.write("PASS " + fromUser[1]+"\n");
                        out.flush();
                        System.out.println("--> PASS " + fromUser[1]); //print out what we send
                        readMultiLine(in);
                        break;
                    case "cd":
                        if (fromUser.length!=2){
                            printErrorMsg("002", "", "");
                            break;
                        }
                        out.write("CWD "+fromUser[1]+"\n");
                        out.flush();
                        System.out.println("--> CWD " + fromUser[1]);
                        readMultiLine(in);
                        break;
                    case "quit":
                        if (fromUser.length!=1){
                            printErrorMsg("002", "", "");
                            break;
                        }
                        out.write("QUIT\n");
                        out.flush();
                        System.out.println("--> QUIT");
                        System.out.println(in.readLine());
                        System.exit(1);
                        break;
                    case "features":
                        if (fromUser.length!=1){
                            printErrorMsg("002", "", "");
                            break;
                        }
                        out.write("FEAT\n");
                        out.flush();
                        System.out.println("--> FEAT");
                        readMultiLine(in);
                        break;
                    case "dir":
                        if (fromUser.length!=1){
                            printErrorMsg("002", "", "");
                            break;
                        }
                        out.write("PASV"+"\n");
                        out.flush();
                        System.out.println("--> PASV");
                        String response = in.readLine();
                        Pattern pattern = Pattern.compile("\\((.*)\\)");
                        Matcher matcher = pattern.matcher(response);

                        try {
                            if (matcher.find()) {
                                String[] IpAdd = matcher.group(1).split(",");
                                subHostname = IpAdd[0] + "." + IpAdd[1] + "." + IpAdd[2] + "." + IpAdd[3];
                                subPortNumber = Integer.parseInt(IpAdd[4]) * 256 + Integer.parseInt(IpAdd[5]);

                                try {
                                    Socket subSocket = new Socket(subHostname, subPortNumber);
                                    BufferedReader inSub = new BufferedReader(
                                            new InputStreamReader(subSocket.getInputStream()));
                                    subSocket.setSoTimeout(IO_TIMEOUT);
                                    out.write("LIST\n");
                                    out.flush();
                                    System.out.println("--> LIST");

                                    String currentLine;
                                    while (true) {
                                        if ((currentLine = inSub.readLine()) != null) {
                                            System.out.println("<-- " + currentLine);
                                        } else {
                                            break;
                                        }
                                    }
                                    inSub.close();
                                    subSocket.close();
                                } catch (SocketTimeoutException e) {
                                    printErrorMsg("3A2", subHostname, Integer.toString(subPortNumber));
                                    continue;
                                }
                            }
                        } catch(IOException e){
                            printErrorMsg("3A7", "", "");
                            continue;
                        }
                        readMultiLine(in);
                        readMultiLine(in);
                        break;
                    case "get":
                        if (fromUser.length!=2){
                            printErrorMsg("002", "", "");
                            break;
                        }
                        out.write("PASV"+"\n");
                        out.flush();
                        System.out.println("--> PASV");
                        response = in.readLine();

                        out.write("TYPE I"+"\n");
                        out.flush();
                        readMultiLine(in);

                        pattern = Pattern.compile("\\((.*)\\)");
                        matcher = pattern.matcher(response);
                        try {
                            if (matcher.find()) {

                                System.out.println("<--" + matcher.group(1));
                                String[] IpAdd = matcher.group(1).split(",");
                                subHostname = IpAdd[0] + "." + IpAdd[1] + "." + IpAdd[2] + "." + IpAdd[3];
                                subPortNumber = Integer.parseInt(IpAdd[4]) * 256 + Integer.parseInt(IpAdd[5]);
                                try {
                                    Socket subSocket = new Socket(subHostname, subPortNumber);
                                    subSocket.setSoTimeout(IO_TIMEOUT); //sets socket timeout for 0x3A2
                                    FileOutputStream tempFile = new FileOutputStream(fromUser[1]);

                                    out.write("RETR " + fromUser[1] + "\n");
                                    out.flush();
                                    System.out.println("--> RETR");
                                    String reply = in.readLine();

                                    //Data transfer connection fail to open
                                    if (reply.startsWith("425")) {
                                        printErrorMsg("3A2", subHostname, Integer.toString(subPortNumber));
                                        break;
                                    }

                                    //fail to access file
                                    if (reply.startsWith("550")) {
                                        printErrorMsg("38E", fromUser[1], "");
                                        break;
                                    }

                                    //save remote file to local machine
                                    int line;
                                    byte[] buf=new byte[4096];
                                    InputStream inSub = subSocket.getInputStream();
                                    while ((line = inSub.read(buf)) > 0) {
                                        tempFile.write(buf,0 ,line);
                                    }

                                    tempFile.close();
                                    tempFile.flush();
                                    inSub.close();
                                    subSocket.close();
                                    readMultiLine(in);

                                } catch(SocketTimeoutException e) {
                                    printErrorMsg("3A2", subHostname, Integer.toString(subPortNumber));
                                    continue;
                                }
                            }
                        } catch(IOException e){
                            printErrorMsg("3A7", "", "");
                            continue;
                        }
                        break;
                    //if not one of these cases, invalid command error
                    default: printErrorMsg("001","","");
                }

            }
        } catch (UnknownHostException e) {
            printErrorMsg("FFFC", hostName, Integer.toString(portNumber));
            System.exit(1);
        } catch (SocketTimeoutException e) {
            printErrorMsg("FFFC", hostName, Integer.toString(portNumber));
            System.exit(1);
        } catch (IOException e) {
            printErrorMsg("FFFD", null, null);
            System.exit(1);
        } catch (Exception e) {
            printErrorMsg("FFFF", null, e.getMessage());
            System.exit(1);
        }
    }

    private static void printErrorMsg(String errorCode, String info1, String info2) {
        switch (errorCode){
            case "001":
                System.err.println("0x"+ errorCode + " Invalid command.\n");
                break;
            case "002":
                System.err.println("0x"+ errorCode + " Incorrect number of arguments.\n");
                break;
            case "38E":
                System.err.println("0x"+ errorCode + " Access to local file " + info1 + " denied.\n");
                break;
            case "FFFC":
                System.err.println("0x"+ errorCode + " Control connection to " + info1+" on port"+ info2 +"failed to open.\n");
                break;
            case "FFFD":
                System.err.println("0x"+ errorCode + " Control connection I/O error, closing control connection.\n");
                break;
            case "3A2":
                System.err.println("0x"+ errorCode + " Data transfer connection to " + info1+" on port "+ info2 +" failed to open.\n");
                break;
            case "3A7":
                System.err.println("0x"+ errorCode + " Data transfer connection I/O error, closing data connection.\n");
                break;
            case "FFFE":
                System.err.println("0x"+ errorCode + " Input error while reading commands, terminating.\n");
                break;
            case "FFFF":
                System.err.println("0x"+ errorCode + " Processing error. "+ info2 +"\n");
                break;
        }
    }

    //read multiple lines from the buffer
    public  static void readMultiLine(BufferedReader in) {
        try {
            char lastLine;
            fromServer = in.readLine();

            while (true) {
                System.out.println("<-- "+ fromServer);
                lastLine = fromServer.charAt(3);
                if (lastLine == ' ') {
                    break;
                }
                fromServer = in.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
