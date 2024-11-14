import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * FileServer类用于创建服务器端，监听TCP和UDP请求，处理文件服务。
 */
public class FileServer {
    private String rootDirectory;
    private ServerSocket tcpServer;
    private DatagramSocket udpServer;
    private ExecutorService threadPool;

    /**
     * 构造函数，接受一个表示根目录的路径。
     * @param rootDir 根目录路径
     * @throws FileNotFoundException 如果指定的根目录无效
     */
    public FileServer(String rootDir) throws FileNotFoundException {
        File dir = new File(rootDir);
        if (!dir.isDirectory()) {
            throw new FileNotFoundException("根目录无效: " + rootDir);
        }
        this.rootDirectory = rootDir;
        this.threadPool = Executors.newCachedThreadPool();
    }

    /**
     * 启动服务器，监听指定的TCP和UDP端口。
     * @throws IOException 如果网络相关错误发生
     */
    public void startServer() throws IOException {
        this.tcpServer = new ServerSocket(2021);
        this.udpServer = new DatagramSocket(2020);

        while (true) {
            Socket clientSocket = tcpServer.accept();
            threadPool.execute(new ClientHandler(clientSocket, rootDirectory, udpServer));
        }
    }

    /**
     * 主方法，用于启动FileServer。
     * @param args 命令行参数，应包括根目录路径
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("需要指定根目录");
            return;
        }

        try {
            FileServer server = new FileServer(args[0]);
            server.startServer();
        } catch (Exception e) {
            System.err.println("服务器启动失败: " + e.getMessage());
        }
    }
}