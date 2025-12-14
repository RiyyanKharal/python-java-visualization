import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class PythonVisualizationApplet extends Applet implements ActionListener {
    TextArea command, result;
    Button send;
    Image picture;
    
    public void init() {
        setLayout(new BorderLayout());
        
        // Input area
        Panel topPanel = new Panel(new BorderLayout());
        topPanel.add(new Label("Enter Python Commands:"), BorderLayout.NORTH);
        command = new TextArea(5, 50);
        topPanel.add(command, BorderLayout.CENTER);
        
        // Button
        send = new Button("Send to Python Server");
        send.addActionListener(this);
        topPanel.add(send, BorderLayout.SOUTH);
        
        // Output area
        Panel centerPanel = new Panel(new BorderLayout());
        centerPanel.add(new Label("Output from Python:"), BorderLayout.NORTH);
        result = new TextArea(10, 50);
        result.setEditable(false);
        centerPanel.add(result, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
    }
    
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == send) {
            String output = "";
            try {
                String sendData = command.getText().trim();
                command.setText("");
                String[] code = sendData.split("\n");
                
                if (sendData.length() > 0) {
                    for (String xcode : code) {
                        Socket s = null;
                        DataInputStream in = null;
                        DataOutputStream out = null;
                        
                        try {
                            s = new Socket("localhost", 1234);
                            in = new DataInputStream(s.getInputStream());
                            out = new DataOutputStream(s.getOutputStream());
                            
                            output = ">>> " + xcode + "\n" + result.getText();
                            result.setText(output);
                            
                            out.write(xcode.trim().getBytes());
                            out.flush();
                            
                            if (xcode.equals("exit()") || xcode.equals("quit()")) {
                                s.close();
                                output = "Disconnected\n" + result.getText();
                                result.setText(output);
                            } else {
                                byte[] bdata = new byte[24576];
                                
                                if (xcode.equals("chart")) {
                                    // Remove existing file if any
                                    try {
                                        File f = new File("plot.jpg");
                                        if (f.exists()) f.delete();
                                    } catch (Exception ex) {}
                                    
                                    // Receive the full image from Python server
                                    FileOutputStream fileOutputStream = new FileOutputStream("plot.jpg");
                                    int bytesRead;
                                    while ((bytesRead = in.read(bdata)) != -1) {
                                        fileOutputStream.write(bdata, 0, bytesRead);
                                    }
                                    fileOutputStream.close();
                                    
                                    // Load image relative to HTML document
                                    picture = getImage(getDocumentBase(), "plot.jpg");
                                    repaint();
                                    
                                    output = ">>> Chart received and displayed\n" + result.getText();
                                    result.setText(output);
                                    
                                } else {
                                    // Receive text data
                                    int bytesReadText = in.read(bdata, 0, bdata.length);
                                    String edata = new String(bdata, 0, bytesReadText);
                                    
                                    if (edata.trim().equals("")) {
                                        output = ">>> " + bytesReadText + "\n" + result.getText();
                                        result.setText(output);
                                    } else {
                                        output = ">>> " + edata + "\n" + result.getText();
                                        result.setText(output);
                                        if (edata.contains("Runtime Error: ")) {
                                            output = "Connection Terminated, reconnect again...\n" + output;
                                            result.setText(output);
                                        }
                                    }
                                }
                            }
                            s.close();
                            
                        } catch (Exception ex) {
                            output = "Error: " + ex.toString() + "\n" + result.getText();
                            result.setText(output);
                        }
                    }
                } else {
                    output = "Type the command to send to the Python server...\n" + result.getText();
                    result.setText(output);
                }
            } catch (Exception e1) {
                output = e1.toString() + "\n" + result.getText();
                result.setText(output);
            }
        }
    }
    
    public void paint(Graphics g) {
        if (picture != null) {
            // Draw the chart at bottom of applet; you can resize as needed
            g.drawImage(picture, 30, 420, getWidth() - 60, 200, this);
        }
    }
}

