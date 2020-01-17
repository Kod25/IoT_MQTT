import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

public class MQTTBroker implements Runnable{

    static final int PORT = 1883;

    private Socket connect;

    static HashMap<byte[], ArrayList<Long>> topicUserList = new HashMap<byte[], ArrayList<Long>> ();

    static HashMap<Long, byte[]> threadSendList = new HashMap<Long, byte[]> ();

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

    static byte[] createUnSubAck(byte[] id) {
        byte[] header = new byte[4];
        header[0] = (byte) 176;
        header[1] = (byte) 2;
        header[2] = id[0];
        header[3] = id[1];
        return header;
    }

    static byte[] createSubAck(byte[] id) {
        byte[] header = new byte[5];
        header[0] = (byte) 144;
        header[1] = (byte) 3;
        header[2] = id[0];
        header[3] = id[1];
        header[4] = id[2];
        return header;
    }

    static boolean parse(byte[] header, byte[] data, BufferedOutputStream out, long threadId) throws IOException {

        int type = (header[0] >> 4) & 0x0F;
        switch (type) {
            case 1:
                parseConnectionMessage(header, data);
                sendMessage(createConAck(), out);
                break;
            case 3:
                System.out.println("Message type: " + type);
                System.out.println("Thread: " + threadId);
                parsePublishMessage(header, data, threadId);
                break;
            case 8:
                System.out.println("Message type: " + type);
                System.out.println("Thread: " + threadId);
                byte[] subId = parseSubscribeMessage(data, threadId);
                sendMessage(createSubAck(subId), out);
                break;
            case 10:
                System.out.println("Message type: " + type);
                System.out.println("Thread: " + threadId);
                byte[] unSubId = parseUnSubscribeMessage(header, data, threadId);
                sendMessage(createUnSubAck(unSubId), out);
                break;
            case 12:
                //System.out.println("Ping");
                sendMessage(createPong(), out);
                break;
            case 14:
                //Disconnect is handled with the false outside the parse function
                return false;
        }
        return true;
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

    static byte[] parseUnSubscribeMessage(byte[] header, byte[] data, long threadId) {
        byte[] id = new byte[2];
        id[0] = data[0];
        id[1] = data[1];
        int pos = 2;
        while(pos < data.length) {
            int tLen = (int) data[pos] & 0xFF << 8;
            tLen += (int) data[pos + 1] & 0xFF;
            pos += 2;
            byte[] topic = Arrays.copyOfRange(data, pos, pos + tLen);
            pos += tLen;
            ArrayList<Long> list = topicUserList.get(topic);
            list.remove(threadId);
            topicUserList.replace(topic, list);
        }
        return id;
    }

    static byte[] parseSubscribeMessage(byte[] data, long threadId){
        byte [] id = new byte[3];
        id[0] = data[0];
        id[1] = data[1];
        int pos = 2;
        while(pos<data.length) {
            int tLen = (int) data[pos] & 0xFF << 8;
            tLen += (int) data[pos+1] & 0xFF;
            pos += 2;
            byte[] topic = Arrays.copyOfRange(data, pos, tLen);
            byte qos = (byte) 0;
            pos += tLen+1;
            ArrayList<Long> list;
            if((list = topicUserList.get(topic)) != null) {
                list.add(threadId);
                topicUserList.replace(topic, list);
                id[2] = (byte) 0x00;
            } else {
                id[2] = (byte) 0x80;
            }
        }
        return id;
    }

    static void parsePublishMessage(byte[] header, byte[] data, long threadId) {
        int removeBytes = 0;
        for(int i = 0; i < 3; i++) {
            if(header[4-i] == 0x00) {
                removeBytes += 1;
            }
        }
        byte[] trueHeader = new byte[5-removeBytes];
        for(int i = 0; i < trueHeader.length; i++) {
            trueHeader[i] = header[i];
        }
        int tLen = (int) data[0] & 0xFF << 8;
        tLen += (int) data[1] & 0xFF;
        byte[] topic = Arrays.copyOfRange(data, 2, tLen);
        ArrayList<Long> list;
        if((list = topicUserList.get(topic)) != null){
            topicUserList.put(topic, new ArrayList<Long>());
            return;
        }
        System.out.println("AsssA");
        byte[] message = new byte[trueHeader.length + data.length];

        System.arraycopy(header, 0, message, 0, trueHeader.length);
        System.arraycopy(data, 0, message, trueHeader.length, data.length);
        System.out.println(trueHeader.length);
        System.out.println(message[2]);
        if(list != null) {
            for (int i = 0; i < list.size(); i++) {
                threadSendList.put(list.get(i), message);
            }
        }
        return;
    }


    static void sendMessage(byte[] data, BufferedOutputStream out) throws IOException {
        out.write(data, 0, data.length);
        out.flush();
        return;
    }


    boolean additionalHeaderByte(byte data) {
        return ((data & 0x80) > 0 );
    }

    static void sendPublish(Long id, BufferedOutputStream out) throws IOException {
        byte[] data;
        if((data = threadSendList.get(id)) != null) {
            sendMessage(data, out);
            threadSendList.remove(id, data);

        }
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
        while (running) {
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
                long threadId = Thread.currentThread().getId();
                running = parse(header, data, out, threadId);

                sendPublish(threadId, out);
            } catch (Exception e) {
                System.err.println(e);
            }
        }
        try {
            connect.close(); // we close socket connection
        } catch (Exception e) {
            System.err.println("Error closing stream : " + e.getMessage());
        }
        System.out.println("Connection closed.\n");
    }
}
