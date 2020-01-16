import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Date;

public class MQTTBroker implements Runnable{

    static final int PORT = 1883;

    private Socket connect;

    public MQTTBroker(Socket c){
        connect = c;
    }

    static byte[] createConAck() {
        byte[] header = new byte[4];
        header[0] = (byte) 32;
        header[1] = (byte) 2;
        header[2] = (byte) 0;
        header[3] = (byte) 0;
        return header;
    }

    static byte[] createPong() {
        byte[] header = new byte[2];
        header[0] = (byte) 208;
        header[1] = (byte) 0;
        return header;
    }

    static void parse(byte[] header, byte[] data, BufferedOutputStream out) throws IOException {
        int type = (header[0] >> 4) & 0x0F;
        for(int i = 0; i < header.length; i++) {
            System.out.println(header[i]);
        }
        System.out.println("header end");
        System.out.println(type);
        switch (type) {
            case 1:
                parseConnectionMessage(header, data);
                sendMessage(createConAck(), out);
                break;
            case 3:
                break;
            case 8:
                break;
            case 10:
                break;
            case 12:
                System.out.println("Ping");
                sendMessage(createPong(), out);
                break;
        }
    }

    static void parseConnectionMessage(byte[] header, byte[] data) {
        int pLen = (int) data[0] & 0xFF << 8;
        pLen += (int) data[1] & 0xFF;
        System.out.println("protocol len: " + pLen);
        byte[] pName = Arrays.copyOfRange(data, 2, 2 + pLen);
        System.out.println("Protocol namn: " + new String(pName));
        System.out.println("protocol level: " + data[2+pLen]);
        int keepAlive = (int) data[pLen + 4] & 0xFF << 8;
        keepAlive += (int) data[pLen + 5] & 0xFF;
        System.out.println("Keep Alive: " + keepAlive);
        int clientIdLength = (int) data[pLen + 6] & 0xFF << 8;
        clientIdLength += (int) data[pLen +7] & 0xFF;
        System.out.println("client length: "+clientIdLength);
        byte[] clientId = Arrays.copyOfRange(data,pLen+8 ,data.length);
        System.out.println("Protocol namn: " + new String(clientId));
    }

    static void sendMessage(byte[] data, BufferedOutputStream out) throws IOException {
        out.write(data, 0, data.length);
        out.flush();
        return;
    }

    boolean additionalHeaderByte(byte data) {
        return ((data & 0x80) > 0 );
    }

    public static void main(String args[]) {
        try {
            final ServerSocket server = new ServerSocket(PORT);
            System.out.println("listening");
            while (true) {
                MQTTBroker myServer = new MQTTBroker(server.accept());

                System.out.println("Connection opened. (" + new Date() + ")");

                Thread thread = new Thread(myServer);
                thread.start();
            }
        }catch(IOException e){
            System.err.println("Connection error" + e.getMessage());
        }

    }

    //@Override
    public void run() {
        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        boolean running = true;
        while (true) {
            try {
                in = new BufferedInputStream(connect.getInputStream());
                out = new BufferedOutputStream(connect.getOutputStream());
                while (connect.getInputStream().available() == 0) {

                }
                byte[] header = new byte[5];
                int readHeaderBytes = 2;


                in.read(header, 0, 2);
                while (additionalHeaderByte(header[readHeaderBytes - 1]) && readHeaderBytes < 6) {
                    header[readHeaderBytes] = (byte) in.read();
                    readHeaderBytes += 1;
                }
                int bodyLength = header[1] & 0x7F;
                bodyLength += (int) (header[2] & 0x7F) << 7;
                bodyLength += (int) (header[3] & 0x7F) << 14;
                bodyLength += (int) (header[4] & 0x7F) << 21;

                byte[] data = new byte[bodyLength];
                int check = in.read(data, 0, bodyLength);
                if (check != bodyLength) {
                    throw new RuntimeException("Kunde inte läsa hela");
                }
                parse(header, data, out);

            } catch (Exception e) {
                System.err.println(e);
            }/*finally{
            try {
                connect.close(); // we close socket connection
            } catch (Exception e) {
                System.err.println("Error closing stream : " + e.getMessage());
            }

            System.out.println("Connection closed.\n");
        }*/
        }
    }
}
