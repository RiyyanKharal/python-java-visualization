import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.InputStream;
import java.net.Socket;

public class JavaSwingClient extends JFrame {
    private JTextArea command, result;
    private JButton sendButton;
    private JLabel imageShow;

    public JavaSwingClient() {
        setTitle("Python-Java Visualization Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        command = new JTextArea(8, 50); // taller
        result = new JTextArea(15, 50);
        result.setEditable(false);
        sendButton = new JButton("Send");
        imageShow = new JLabel();
        imageShow.setHorizontalAlignment(JLabel.CENTER);

        // Use a vertical BoxLayout for left panel
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

        leftPanel.add(new JLabel("Commands:"));
        leftPanel.add(new JScrollPane(command));
        leftPanel.add(Box.createVerticalStrut(10)); // spacing
        leftPanel.add(new JLabel("Results:"));
        leftPanel.add(new JScrollPane(result));
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(imageShow); // graph under the results

        JPanel rightPanel = new JPanel(); // for button
        rightPanel.add(sendButton);

        add(leftPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        sendButton.addActionListener(this::sendCommands);

        pack();
        setVisible(true);
    }

    private void sendCommands(ActionEvent e) {
        String sendData = command.getText().trim();
        command.setText("");
        String output = result.getText();

        try (Socket s = new Socket("localhost", 1234)) {
            s.getOutputStream().write((sendData + "\n").getBytes());
            s.getOutputStream().flush();

            InputStream in = s.getInputStream();
            in.mark(4);
            byte[] lenBytes = new byte[4];
            int n = in.read(lenBytes);

            if (n == 4) { // image
                int dataLen = ((lenBytes[0] & 0xFF) << 24) |
                              ((lenBytes[1] & 0xFF) << 16) |
                              ((lenBytes[2] & 0xFF) << 8) |
                              (lenBytes[3] & 0xFF);

                byte[] imageBytes = new byte[dataLen];
                int offset = 0;
                while (offset < dataLen) {
                    int read = in.read(imageBytes, offset, dataLen - offset);
                    if (read == -1) break;
                    offset += read;
                }

                ImageIcon icon = new ImageIcon(imageBytes);
                imageShow.setIcon(icon);
                imageShow.revalidate();
                imageShow.repaint();
                output += "Plot displayed\n";

            } else { // text
                in.reset();
                byte[] buf = new byte[24576];
                int readLen = in.read(buf);
                if (readLen > 0) {
                    String text = new String(buf, 0, readLen);
                    text = text.replace("\nEND_OF_TEXT\n", "");
                    output += text + "\n";
                }
            }

            result.setText(output);

        } catch (Exception ex) {
            ex.printStackTrace();
            result.setText(output + "\nError: " + ex);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(JavaSwingClient::new);
    }
}

