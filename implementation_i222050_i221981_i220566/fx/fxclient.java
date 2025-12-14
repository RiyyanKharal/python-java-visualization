import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

public class JavaFXClient extends Application {
    private TextArea command, result;
    private Button sendButton;
    private ImageView imageView;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Python-Java Visualization Client");

        // Components
        command = new TextArea();
        command.setPrefRowCount(5);
        result = new TextArea();
        result.setEditable(false);
        sendButton = new Button("Send");
        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        // Layout
        VBox root = new VBox(10);
        root.getChildren().addAll(
            new Label("Commands:"),
            command,
            new Label("Results:"),
            result,
            sendButton,
            imageView
        );

        // Button action
        sendButton.setOnAction(e -> sendCommands());

        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    private void sendCommands() {
        String sendData = command.getText().trim();
        command.setText("");
        String[] codeLines = sendData.split("\n");
        String output = result.getText();

        for (String xcode : codeLines) {
            if (xcode.trim().isEmpty()) continue;
            output = processCommand(xcode.trim(), output);

            if (xcode.equals("chart")) {
                try {
                    InputStream stream = new FileInputStream("plot.jpg"); // Python server must save this file
                    Image image = new Image(stream);
                    imageView.setImage(image);
                } catch (Exception e) {
                    output += "Error loading image: " + e.toString() + "\n";
                }
            }
        }

        result.setText(output);
    }

    private String processCommand(String xcode, String output) {
        try (Socket socket = new Socket("localhost", 1234)) {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            output = ">>> " + xcode + "\n" + output;

            // Send command to Python server
            out.write((xcode + "\n").getBytes("UTF-8"));
            out.flush();

            // Handle exit command
            if (xcode.equals("exit()") || xcode.equals("quit()")) {
                return "Disconnected from Python server.\n" + output;
            }

            // Handle chart separately (Python server saves plot.jpg)
            if (!xcode.equals("chart")) {
                byte[] buffer = new byte[4096];
                int bytesRead = in.read(buffer);
                if (bytesRead > 0) {
                    String response = new String(buffer, 0, bytesRead, "UTF-8");
                    output += response + "\n";
                }
            }

        } catch (Exception e) {
            output += "ERROR: " + e.getMessage() + "\n";
        }

        return output;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

