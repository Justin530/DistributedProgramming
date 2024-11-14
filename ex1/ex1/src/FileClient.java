import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * FileClient 类用于连接服务器并发送命令，接收响应。
 */
public class FileClient {
    private Socket tcpSocket;
    private DatagramSocket udpSocket;
    private BufferedWriter out;
    private BufferedReader in;
    private Scanner scanner;
    private static final int BUFFER_SIZE = 2048; // 缓存大小

    /**
     * 构造函数，连接到服务器的TCP端口。
     * @param serverAddress 服务器的IP地址
     * @param tcpPort 服务器的TCP端口号
     * @throws IOException 如果连接出错
     */
    public FileClient(String serverAddress, int tcpPort) throws IOException {
        tcpSocket = new Socket(serverAddress, tcpPort);
        out = new BufferedWriter(new OutputStreamWriter(tcpSocket.getOutputStream()));
        in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
        udpSocket = new DatagramSocket(tcpSocket.getPort() + 1);
        scanner = new Scanner(System.in);
    }

    /**
     * 启动客户端，接收用户输入并处理响应。
     */
    public void start() {
        try {
            System.out.println("服务器响应：");
//            out.flush();
            if (!readResponse().contains("连接成功")) {
                System.out.println("服务器连接失败.");
                return;
            }

            String userInput;
            while (true) {
                System.out.print("请输入命令: ");
                userInput = scanner.nextLine();
                sendCommand(userInput);

                if ("bye".equalsIgnoreCase(userInput)) {
                    break;
                }
                out.flush();
                String serverResponse = readResponse();
                if (userInput.startsWith("get ") && serverResponse.contains("OK")) {
                    receiveFile(userInput.split(" ")[1]);
                }
            }
        } catch (IOException e) {
            System.out.println("客户端错误: " + e.getMessage());
        } finally {
            try {
                tcpSocket.close();
                udpSocket.close();
            } catch (IOException e) {
                System.out.println("关闭连接时发生错误: " + e.getMessage());
            }
        }
    }

    /**
     * 从服务器读取响应直到空行。
     *
     * @return 服务器响应的字符串
     * @throws IOException 如果读取出错
     */
    private String readResponse() throws IOException {
        StringBuilder response = new StringBuilder();
        String responseLine;
        while (in.ready()) {
            responseLine = in.readLine();
            if (responseLine == null || responseLine.isEmpty()) {
                break;
            }
            response.append(responseLine).append("\n");
            System.out.println(responseLine);
        }
        return response.toString();
    }

    /**
     * 向服务器发送命令并手动刷新输出流。
     *
     * @param command 要发送的命令
     * @throws IOException 如果写入时发生错误
     */
    private void sendCommand(String command) throws IOException {
        out.write(command);
        out.newLine(); // 添加换行符
        out.flush();   // 刷新输出流，确保数据立即发送
    }

    /**
     * 接收文件。
     * @param filename 文件名
     */
    private void receiveFile(String filename) throws IOException {
        System.out.println("将在UDP端口 " + udpSocket.getLocalPort() + " 上接收文件: " + filename);
        udpSocket.setSoTimeout(5000); // 设置5秒超时

        // 接收文件大小信息
        byte[] sizeBuffer = new byte[2048];
        DatagramPacket sizePacket = new DatagramPacket(sizeBuffer, sizeBuffer.length);
        udpSocket.receive(sizePacket);
        long fileSize = Long.parseLong(new String(sizePacket.getData(), 0, sizePacket.getLength()).trim());
        System.out.println("文件大小: " + fileSize + " bytes");

        File file = new File(filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            System.out.println("开始接收文件: " + filename);

            int receivedBytes = 0;
            while (receivedBytes < fileSize) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    udpSocket.receive(packet);
                } catch (SocketTimeoutException e) {
                    System.out.println("接收超时，停止接收文件。");
                    break;
                }
                fos.write(buffer, 0, packet.getLength());
                receivedBytes += packet.getLength();
                System.out.println("接收文件数据: " + packet.getLength() + " bytes");
            }
        }
        System.out.println("文件接收完成：" + filename);
    }

    /**
     * 主方法，用于启动FileClient。
     * @param args 命令行参数，包括服务器地址和端口号
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java FileClient <server address> <TCP port>");
            return;
        }
        try {
            FileClient client = new FileClient(args[0], Integer.parseInt(args[1]));
            client.start();
        } catch (IOException e) {
            System.err.println("无法连接到服务器: " + e.getMessage());
        }
    }
}
