import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

/**
 * ClientHandler 类用于处理与单个客户端的连接，执行客户端发送的命令，并通过TCP和UDP进行数据传输。
 */
public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private File currentDirectory;
    private File rootDirectory;
    private DatagramSocket udpSocket;

    /**
     * 构造函数，初始化客户端处理器。
     * @param socket 客户端连接的socket
     * @param rootDirectory 服务端的根目录路径
     * @param udpSocket UDP端口用于文件传输
     */
    public ClientHandler(Socket socket, String rootDirectory, DatagramSocket udpSocket) {
        this.clientSocket = socket;
        this.rootDirectory = new File(rootDirectory);
        this.currentDirectory = this.rootDirectory;
        this.udpSocket = udpSocket;
    }

    /**
     * 运行处理器，处理来自客户端的命令并响应。
     */
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            // 通知客户端连接成功
            out.write("客户端IP地址:" + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort() + ">连接成功");
            //out.newLine();  // 添加新行
            out.flush();  // 刷新输出流，以确保数据立即发送给客户端

            out.write(""); // 发送空行以结束响应
            out.newLine(); // 添加新行
            out.flush();  // 刷新输出流

            String inputLine;
            while (true) {
                inputLine = in.readLine();
                System.out.println(inputLine);
                if ("bye".equals(inputLine.toLowerCase())) {
                    out.write("断开连接");
                    out.newLine(); // 添加新行
                    out.flush(); // 刷新输出流
                    break;
                }
                processCommand(inputLine, out);
                out.newLine();  // 结束每个命令响应后发送空行
                out.flush();    // 刷新输出流
            }
        } catch (IOException e) {
            System.out.println("处理客户端请求时发生错误: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("关闭客户端socket时发生错误: " + e.getMessage());
            }
        }
    }

    /**
     * 根据输入的命令，调用相应的处理方法。
     * @param command 客户端发送的命令
     * @param out 用于向客户端发送响应的PrintWriter
     */
    private void processCommand(String command, BufferedWriter out) throws IOException {
        String[] tokens = command.split(" ");
        try {
            switch (tokens[0].toLowerCase()) {
                case "ls":
                    listDirectoryContents(out);
                    break;
                case "cd":
                    changeDirectory(tokens, out);
                    break;
                case "get":
                    prepareFileForTransfer(tokens[1], out);
                    break;
                default:
                    out.write("unknown cmd");
                    out.newLine();
                    out.flush();
            }
        } catch (Exception e) {
            out.write("错误: " + e.getMessage());
            out.newLine();
            out.flush();
        }
    }

    /**
     * 列出当前目录下的文件和目录。
     * @param out 用于向客户端发送数据的PrintWriter
     */
    private void listDirectoryContents(BufferedWriter out) throws IOException {
        File[] files = currentDirectory.listFiles();
        for (File file : files) {
            out.write((file.isDirectory() ? "<dir>" : "<file>") + "\t" + file.getName() + "\t" + file.length());
            out.newLine();
            out.flush();
        }
    }

    /**
     * 根据客户端请求更改当前目录。
     * @param tokens 客户端发送的命令分割得到的字符串数组
     * @param out 用于向客户端发送响应的PrintWriter
     */
    private void changeDirectory(String[] tokens, BufferedWriter out) throws IOException {
        if (tokens.length > 1) {
            File newDir = new File(currentDirectory, tokens[1]);
            if ("..".equals(tokens[1])) {
                if (currentDirectory.equals(rootDirectory)) {
                    out.write("已经在根目录");
                    out.newLine();
                    out.flush();
                } else {
                    currentDirectory = currentDirectory.getParentFile();
                    out.write("当前目录已更改为：" + currentDirectory.getPath());
                    out.newLine();
                    out.flush();
                }

            } else if (newDir.isDirectory()) {
                currentDirectory = newDir;
                out.write("当前目录已更改为：" + currentDirectory.getPath());
                out.newLine();
                out.flush();
            } else {
                out.write("unknown dir");
                out.newLine();
                out.flush();
            }
        } else {
            out.write("缺少目录参数");
            out.newLine();
            out.flush();
        }
    }

    /**
     * 准备通过UDP发送文件，首先确认文件存在。
     * @param filename 要传输的文件名
     * @param out 用于向客户端发送响应的PrintWriter
     */
    private void prepareFileForTransfer(String filename, BufferedWriter out) throws IOException {
        File file = new File(currentDirectory, filename);
        if (!file.exists() || file.isDirectory()) {
            out.write("unknown file");
            out.newLine();
            out.flush();
        } else {
            out.write("OK");
            out.newLine();
            out.flush();
            out.write(file.getPath() + "\t" + file.length());
            out.newLine();
            out.flush();
            sendFileSize(file);
            sendFile(file);
        }
    }

    /**
     * 通过UDP发送文件。
     * @param file 要发送的文件
     */
    private void sendFile(File file) throws IOException {
        byte[] buffer = new byte[2048];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytes;
            int clientPort = 2022; // 客户端接收文件的端口
            InetAddress clientAddress = clientSocket.getInetAddress(); // 获取客户端的地址

            while ((bytes = fis.read(buffer)) > 0) {
                DatagramPacket packet = new DatagramPacket(buffer, bytes, clientAddress, clientPort);
                udpSocket.send(packet);
                System.out.println("发送文件数据: " + bytes + " bytes");
                Thread.sleep(10); // 控制发送速率
            }
            System.out.println("文件发送完成：" + file.getName() + "到端口" + clientPort);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 发送文件大小信息。
     * @param file 要发送的文件
     */
    private void sendFileSize(File file) throws IOException {
        // 发送文件大小信息
        byte[] sizeInfo = String.valueOf(file.length()).getBytes();
        DatagramPacket sizePacket = new DatagramPacket(sizeInfo, sizeInfo.length, clientSocket.getInetAddress(), 2022);
        udpSocket.send(sizePacket);
    }
}
