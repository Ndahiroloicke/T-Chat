import java.net.*;
import java.io.*;
import java.util.*;

public class GroupChat {
    private static final String TERMINATE = "Exit";
    private static final String MULTICAST_IP = "225.0.0.1"; // Updated multicast IP
    static String name;
    static volatile boolean finished = false;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // Prompt user for name
        System.out.print("Enter your name: ");
        name = sc.nextLine();

        // Display available options
        System.out.println("\nOptions:");
        System.out.println("1. Join an existing channel");
        System.out.println("2. Create a new channel");
        System.out.print("\nEnter your choice (1 or 2): ");

        int choice = sc.nextInt();
        sc.nextLine(); // Consume the newline character

        int port;

        if (choice == 1) {
            // Display available channels
            System.out.println("\nAvailable Channels:");
            System.out.println("1. Tech Talk (Port 5000)");
            System.out.println("2. Music Chat (Port 5001)");
            System.out.println("3. General Discussion (Port 5002)");
            System.out.print("\nEnter the port number to join a private channel or choose one of the above public channels: ");

            port = sc.nextInt();
            sc.nextLine(); // Consume the newline character
        } else if (choice == 2) {
            // Create a new channel
            System.out.print("\nEnter a unique port number for the new channel: ");
            port = sc.nextInt();
            sc.nextLine(); // Consume the newline character
            System.out.println("\nNew channel created on port: " + port);
        } else {
            System.out.println("Invalid choice. Exiting...");
            return;
        }

        try {
            InetAddress group = InetAddress.getByName(MULTICAST_IP);
            MulticastSocket socket = new MulticastSocket(port);

            // Set TTL for LAN communication
            socket.setTimeToLive(1);

            // Find and bind to the Wi-Fi network interface
            NetworkInterface wifiInterface = findWiFiInterface();
            if (wifiInterface != null) {
                socket.setNetworkInterface(wifiInterface);
                System.out.println("Bound to Wi-Fi interface: " + wifiInterface.getDisplayName());
            } else {
                System.out.println("Wi-Fi interface not found. Ensure Wi-Fi is active.");
                return;
            }

            // Join the multicast group
            socket.joinGroup(group);

            // Start a thread to read messages
            Thread t = new Thread(new ReadThread(socket, group, port));
            t.start();

            // Send messages
            System.out.println("\nStart typing messages (type 'Exit' to leave):");
            while (true) {
                String message = sc.nextLine();

                if (message.equalsIgnoreCase(TERMINATE)) {
                    finished = true;
                    socket.leaveGroup(group);
                    socket.close();
                    break;
                }

                message = name + ": " + message;
                byte[] buffer = message.getBytes();
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, group, port);
                socket.send(datagram);
            }
        } catch (SocketException se) {
            System.out.println("Error creating socket: Check if the port is in use or multicast traffic is blocked.");
            se.printStackTrace();
        } catch (IOException ie) {
            System.out.println("Error reading/writing from/to socket: Ensure the network supports multicast traffic.");
            ie.printStackTrace();
        }
    }

    // Find the Wi-Fi network interface
    private static NetworkInterface findWiFiInterface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                String name = networkInterface.getDisplayName().toLowerCase();
                if (name.contains("wi-fi") || name.contains("wlan")) { // Match common Wi-Fi names
                    return networkInterface;
                }
            }
        }
        return null; // Return null if no Wi-Fi interface is found
    }
}

class ReadThread implements Runnable {
    private MulticastSocket socket;
    private InetAddress group;
    private int port;
    private static final int MAX_LEN = 1000;

    ReadThread(MulticastSocket socket, InetAddress group, int port) {
        this.socket = socket;
        this.group = group;
        this.port = port;
    }

    @Override
    public void run() {
        while (!GroupChat.finished) {
            byte[] buffer = new byte[MAX_LEN];
            DatagramPacket datagram = new DatagramPacket(buffer, buffer.length, group, port);

            try {
                socket.receive(datagram);
                String message = new String(buffer, 0, datagram.getLength(), "UTF-8");

                // Avoid printing the sender's own messages
                if (!message.startsWith(GroupChat.name)) {
                    System.out.println(message);
                }
            } catch (IOException e) {
                if (GroupChat.finished) {
                    System.out.println("Socket closed!");
                } else {
                    System.out.println("Error receiving message: Ensure the multicast address and port are correct.");
                    e.printStackTrace();
                }
            }
        }
    }
}
