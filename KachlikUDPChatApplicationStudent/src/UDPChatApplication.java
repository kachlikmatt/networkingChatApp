import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
//edited by Matthew Kachlik
interface SharedVars
{

    // People information stuff
    StringBuilder[] peopleNames = new StringBuilder[3];
    StringBuilder[] ipAddStrings = new StringBuilder[3];
    Integer[] portNumbers = new Integer[3];

    // User interface stuff
    ImageView[] imageViews = new ImageView[3];
    CheckBox[] recipientCb = new CheckBox[]
    {
        null, // index 0 is for the local person
        new CheckBox("Recipient 1"), new CheckBox("Recipient 2"),
    };
    TextArea chatHistoryTextArea = new TextArea();
    TextField messageTf = new TextField();

    // Network Communications stuff, only one socket
    DatagramSocket[] dSockets = new DatagramSocket[1];
    InetAddress[] iNetAddresses = new InetAddress[3];

    final int you = 0;
    final int buddy1 = 1;
    final int buddy2Pos = 2;
    final int buddy1IP = 2;
    final int buddy2 = 3;
    final int buddy2IP = 4;
    final int buddy1Port = 6;
    final int buddy2Port = 7;

}

public class UDPChatApplication extends Application implements SharedVars
{

    @Override
    public void start(Stage stage) throws Exception
    {
        BorderPane outerPane = new BorderPane();

        // Menu Stuff
        MenuBar menuBar = new MenuBar();

        Menu sendPicMenu = new Menu("Send Pic Menu");
        menuBar.getMenus().add(sendPicMenu);
        MenuItem selectPicMenuItem = new MenuItem("Select a Pic ...");
        sendPicMenu.getItems().add(selectPicMenuItem);

        Menu peopleMenu = new Menu("Participants");
        menuBar.getMenus().add(peopleMenu);
        MenuItem peopleInfoMenuItem = new MenuItem("Names and Network Addresses...");
        peopleMenu.getItems().add(peopleInfoMenuItem);

        outerPane.setTop(menuBar);

        // Center Pane is a VBox containing a VBox with radio buttons
        // and a ChatHistoryTextArea.
        VBox centerPane = new VBox(10);
        VBox recipientsBox = new VBox(5);
        recipientsBox.setStyle("-fx-border-color:gray");
        recipientsBox.setPadding(new Insets(10));
        HBox labelHBox = new HBox();
        labelHBox.getChildren().add(new Label("Select recipients of next message"));
        labelHBox.setAlignment(Pos.CENTER);
        HBox recipientsChecksHBox = new HBox(50);
        recipientsChecksHBox.setAlignment(Pos.CENTER);

        recipientsChecksHBox.getChildren().addAll(recipientCb[1], recipientCb[2]);
        recipientsBox.getChildren().addAll(labelHBox, recipientsChecksHBox);

        chatHistoryTextArea.setEditable(false);
        centerPane.getChildren().addAll(recipientsBox, chatHistoryTextArea);
        centerPane.setPadding(new Insets(10));
        outerPane.setCenter(centerPane);

        // Side bar holds the images of cars sent.
        VBox imageSideBar = new VBox();
        imageSideBar.setAlignment(Pos.CENTER);

        for (int k = 0; k < imageViews.length; k++)
        {
            imageViews[k] = new ImageView(new Image("CarImages/car0.png"));
            imageSideBar.getChildren().add(imageViews[k]);
            imageViews[k].setFitWidth(75);
            imageViews[k].setPreserveRatio(true);
            imageViews[k].setSmooth(true);
        }
        for (int j = 0; j <= 2; j++)
        {
            peopleNames[j] = new StringBuilder();
            ipAddStrings[j] = new StringBuilder();

        }
        imageSideBar.setPadding(new Insets(5));
        outerPane.setLeft(imageSideBar);

        messageTf.setPromptText("Type chat message to send here");
        outerPane.setBottom(messageTf);
        BorderPane.setMargin(messageTf, new Insets(10));

        // Set handlers
        peopleInfoMenuItem.setOnAction((ActionEvent t) ->
        {
            List<String> info = new NamesAndAddressesPane("UDP select",
                    "select").showDialog();
            //storing peoplesNames 
            peopleNames[you].append(info.get(you));

            peopleNames[buddy1].append(info.get(buddy1));
            peopleNames[buddy2Pos].append(info.get(buddy2));
            //chaning the recipents names
            recipientCb[buddy1].setText(peopleNames[buddy1].toString());
            recipientCb[buddy2Pos].setText(peopleNames[buddy2Pos].toString());
            //placing the ports and IP away 
            portNumbers[buddy1] = Integer.parseInt(info.get(buddy1Port));
            portNumbers[buddy2Pos] = Integer.parseInt(info.get(buddy2Port));
            try
            {
                iNetAddresses[buddy1] = InetAddress.getByName(
                        info.get(buddy1IP));
                iNetAddresses[buddy2Pos] = InetAddress.getByName(
                        info.get(buddy2IP));
                int hostPort = Integer.parseInt(info.get(5));
                dSockets[0] = new DatagramSocket(hostPort);
                new Thread(new NetInputHandler()).start();

            } catch (UnknownHostException | SocketException ex)
            {
                Logger.getLogger(UDPChatApplication.class.getName()).log(Level.SEVERE, null, ex);
            }

        });

        // Set handler on messageTf to send a text message
        sendPicMenu.setOnAction((ActionEvent event) ->
        {
            try
            {
                byte[] buffer = new byte[30 * 1024];
                
                FileChooser fileChooser = new FileChooser();
                File file = fileChooser.showOpenDialog(null);
                Path path = file.toPath();

                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                DataOutputStream outStream = new DataOutputStream(byteStream);
                ////end
                BinStringIO.writeString(outStream, "image");
                BinStringIO.writeString(outStream, peopleNames[you].toString());
                Files.copy(path, outStream);
                outStream.flush();
                
                buffer = byteStream.toByteArray();
                if (recipientCb[buddy1].isSelected()
                        && recipientCb[buddy2Pos].isSelected())
                {

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                            iNetAddresses[buddy1], portNumbers[buddy1]);
                    dSockets[0].send(packet);

                    packet = new DatagramPacket(buffer, buffer.length,
                            iNetAddresses[buddy2Pos], portNumbers[buddy2Pos]);
                    dSockets[0].send(packet);

                } else if (recipientCb[buddy1].isSelected())
                {

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                            iNetAddresses[buddy1], portNumbers[buddy1]);
                    dSockets[0].send(packet);
                } else if (recipientCb[buddy2Pos].isSelected())
                {

                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                            iNetAddresses[buddy2Pos], portNumbers[buddy2Pos]);
                    dSockets[0].send(packet);
                } 
                //placing the sent image for the image that was snet
               BufferedImage bufferedImage = ImageIO.read(file);
                Image image = SwingFXUtils.toFXImage(bufferedImage, null);
                imageViews[0].setImage(image);
                //place into buffer then send out
            } catch (IOException ex)
            {
                Logger.getLogger(UDPChatApplication.class.getName()).log(Level.SEVERE, null, ex);
            }
            
               
        });

        messageTf.setOnAction((ActionEvent event) ->
        {
            String message = messageTf.getText().trim();
            //put name into strings
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(byteStream);
            try
            {
                outStream.flush();

                if (recipientCb[buddy1].isSelected()
                        && recipientCb[buddy2Pos].isSelected())
                {
                    if(message.equalsIgnoreCase("bye"))
                    {
                        BinStringIO.writeString(outStream, "end");
                    }
                    else
                    {
                    BinStringIO.writeString(outStream, "chat");
                    }
                    BinStringIO.writeString(outStream, peopleNames[you].toString());
                    BinStringIO.writeString(outStream, message);
                    byte[] wBuffer = byteStream.toByteArray();
                    chatHistoryTextArea.appendText("to " + peopleNames[buddy1] + ">"
                            + message + "\n");

                    DatagramPacket packet = new DatagramPacket(wBuffer, wBuffer.length,
                            iNetAddresses[buddy1], portNumbers[buddy1]);
                    dSockets[0].send(packet);

                    chatHistoryTextArea.appendText("to " + peopleNames[buddy2Pos] + ">"
                            + message + "\n");
                    packet = new DatagramPacket(wBuffer, wBuffer.length,
                            iNetAddresses[buddy2Pos], portNumbers[buddy2Pos]);
                    dSockets[0].send(packet);
                    if(message.equalsIgnoreCase("bye"))
                    {
                        dSockets[0].close();
                    }

                } else if (recipientCb[buddy1].isSelected())
                {
                    BinStringIO.writeString(outStream, "chat");
                    BinStringIO.writeString(outStream, peopleNames[you].toString());
                    BinStringIO.writeString(outStream, message);
                    byte[] wBuffer = byteStream.toByteArray();
                    chatHistoryTextArea.appendText("to " + peopleNames[buddy1] + ">"
                            + message + "\n");

                    DatagramPacket packet = new DatagramPacket(wBuffer, wBuffer.length,
                            iNetAddresses[buddy1], portNumbers[buddy1]);
                    dSockets[0].send(packet);
                } else if (recipientCb[buddy2Pos].isSelected())
                {

                    BinStringIO.writeString(outStream, "chat");
                    BinStringIO.writeString(outStream, peopleNames[you].toString());
                    BinStringIO.writeString(outStream, message);
                    byte[] wBuffer = byteStream.toByteArray();
                    chatHistoryTextArea.appendText("to " + peopleNames[buddy2Pos] + ">"
                            + message + "\n");
                    wBuffer = message.getBytes();
                    DatagramPacket packet = new DatagramPacket(wBuffer, wBuffer.length,
                            iNetAddresses[buddy2Pos], portNumbers[buddy2Pos]);
                    dSockets[0].send(packet);
                }
            } catch (IOException ex)
            {
                Logger.getLogger(UDPChatApplication.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

        /**
         * Put your own code here.
         */
        // Set handler on  Send Pic menu item to send an image
        /**
         * Put your own code here.
         */
        // Set handler on the Participants menu item
        /**
         * Put your own code here.
         */
        // Set the scene on the stage and show the stage.
        Scene scene = new Scene(outerPane);
        stage.setScene(scene);
        stage.setTitle("UDP Chat Application");

        stage.show();

    }

    public static void main(String[] args)
    {
        launch();
    }

}

/**
 * Dialog box to get text input from the user. Use one of the two constructors
 * to create an NamesAndAddressesPane object and call its instance method
 * showDialog(). Returns a list Strings as explained in the comments below the
 * text field.
 *
 * @author gcm
 */
class NamesAndAddressesPane extends Stage
{

    private final TextField[] tFs = new TextField[8];

    /**
     *
     * @param title
     * @param prompt
     * @param defaultInput
     */
    public NamesAndAddressesPane(String title, String prompt)
    {
        VBox vBox = new VBox(10);
        vBox.setPadding(new Insets(10));

        Button oKButton = new Button("Ok");

        GridPane peoplePane = new GridPane();
        peoplePane.setVgap(10);
        peoplePane.setHgap(10);
        String[] personLabels =
        {
            "You: ", "Buddy #1: ", "Buddy #2: "
        };
        for (int row = 0; row < personLabels.length; row++)
        {
            peoplePane.add(new Label(personLabels[row]), 0, row);
        }

        // Order of text fields is local person (0)
        // name of buddy 1 (1), ip address of buddy 1,(2)
        // name of buddy 2, (3), ip address of buddy 2 (4)
        // your port # (5), buddy 1 port # (6), buddy 2 port # (7)
        tFs[0] = new TextField();
        tFs[0].setPromptText("Name");

        for (int k = 1; k < 5; k++)
        {
            tFs[k] = new TextField();
            if (k % 2 == 1)
            {
                tFs[k].setPromptText("Name");
            } else
            {
                tFs[k].setPromptText("IP Address");
                tFs[k].setText("127.0.0.1");
            }
        }
        for (int k = 5; k < tFs.length; k++)
        {
            tFs[k] = new TextField();
            tFs[k].setPromptText("Port number");
            tFs[k].setText("50000");
        }
        // Positions in Gridpane
        int[][] gridPos =
        {
            {
                1, 0
            },
            {
                1, 1
            },
            {
                2, 1
            },
            {
                1, 2
            },
            {
                2, 2
            },
            {
                3, 0
            },
            {
                3, 1
            },
            {
                3, 2
            },
        };

        for (int k = 0; k < tFs.length; k++)
        {
            peoplePane.add(tFs[k], gridPos[k][0], gridPos[k][1]);
        }

        HBox hBox = new HBox(10);
        hBox.getChildren().add(oKButton);
        hBox.setAlignment(Pos.CENTER_RIGHT);
        vBox.getChildren().addAll(peoplePane, hBox);

        Scene scene = new Scene(vBox);
        this.setScene(scene);
        this.setTitle(title);

        this.initModality(Modality.WINDOW_MODAL);

        oKButton.setOnAction(evt ->
        {
            //input = tF.getText();
            this.hide();
        });
    }

    /**
     *
     * @return an list of strings of 0) Your name 1) buddy1 name 2) buddy1 IP
     * address, 3) buddy 2 4) buddy 2 IP address
     */
    public List<String> showDialog()
    {
        this.showAndWait();
        Stream<TextField> stream = Arrays.stream(tFs);
        return stream.map(tf -> tf.getText().trim()).collect(
                Collectors.toList());
    }
}

class NetInputHandler implements Runnable, SharedVars
{

    @Override
    public void run()
    {
        //keep reading until the message is bye
        while (true)
        {
            byte[] rBuffer = new byte[30 * 1024];
            DatagramPacket packet = new DatagramPacket(rBuffer, rBuffer.length);
            try
            {
                dSockets[0].receive(packet);
            } catch (IOException ex)
            {
                Logger.getLogger(NetInputHandler.class.getName()).log(Level.
                        SEVERE, null, ex);
            }

            try
            {
                ByteArrayInputStream byteInStream = new ByteArrayInputStream(
                        packet.getData());
                DataInputStream inStream = new DataInputStream(byteInStream);
                String type = BinStringIO.readString(inStream);
                String sender = BinStringIO.readString(inStream);
               // String message = BinStringIO.(inStream);

                //String input = message;
                if (type.equalsIgnoreCase("chat"))
                {
                    Platform.runLater(() ->
                    {
                        try
                        {
                            String input = BinStringIO.readString(inStream);
                            handleText(sender, input);
                        } catch (IOException ex)
                        {
                            Logger.getLogger(NetInputHandler.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                }
                else if (type.equalsIgnoreCase("image"))
                {
                    Platform.runLater(() ->
                    {
                        try
                        {
                            byteInStream.read(rBuffer);
                        } catch (IOException ex)
                        {
                            Logger.getLogger(NetInputHandler.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        
                        handleImage(sender, rBuffer);
                    });
                    
                }
                else if(type.equalsIgnoreCase("done"))
                {
                    System.out.println("done");
                    Platform.runLater(() ->
                    {
                        try
                        {
                            String input = BinStringIO.readString(inStream);
                             handleText(sender, input);
                            dSockets[0].close();
                            
                        } catch (IOException ex)
                        {
                            Logger.getLogger(NetInputHandler.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                    inStream.close();
                    break;
                }
            } catch (IOException ex)
            {
                Logger.getLogger(NetInputHandler.class.getName()).log(Level.SEVERE,
                        null, ex);
            }

        }
          
    }

    void handleImage(String sender, byte[] imageB)
    {
        Image image = new Image(new ByteArrayInputStream(imageB));
        //deteerming which image panel to update based off the name of sender
       
        if(recipientCb[buddy1].getText().equalsIgnoreCase(sender))
        {
          imageViews[1].setImage(image);  
        }
        else if(recipientCb[buddy2Pos].getText().equalsIgnoreCase(sender))
        {
            imageViews[2].setImage(image); 
        }

        
        
    }

    void handleText(String sender, String message)
    {
        chatHistoryTextArea.appendText(String.format("%s>%s\n", sender, message));
    }
}

interface BinStringIO
{

    static String readString(DataInputStream in) throws IOException
    {
        int messageLen = in.readInt();
        byte[] messageBuffer = new byte[messageLen];
        in.readFully(messageBuffer);
        return new String(messageBuffer);
    }

    static void writeString(DataOutputStream out, String str) throws IOException
    {
        byte[] strBytes = str.getBytes();
        out.writeInt(strBytes.length);
        out.write(strBytes);
    }
}

// Information Box to display a message to the user.
// Always returns InformationBox.OK.
class InformationBox extends Stage
{

    public static final int OK = 1;
    int returnValue;

    public InformationBox(String title, String message)
    {
        VBox vBox = new VBox(10);
        vBox.setPadding(new Insets(10));
        Label promptLabel = new Label(message);
        Button oKButton = new Button("OK");
        HBox iPHBox = new HBox(10);
        iPHBox.setAlignment(Pos.CENTER);
        iPHBox.getChildren().addAll(promptLabel);

        HBox hBox = new HBox(10);
        hBox.getChildren().addAll(oKButton);
        hBox.setAlignment(Pos.CENTER);
        vBox.getChildren().addAll(iPHBox, hBox);

        Scene scene = new Scene(vBox);
        this.setScene(scene);
        this.setTitle(title);

        this.initModality(Modality.WINDOW_MODAL);

        oKButton.setOnAction(evt ->
        {
            returnValue = OK;
            this.hide();
        });
    }

    public static int showDialog(String title, String message)
    {
        InformationBox mBox = new InformationBox(title, message);
        mBox.showAndWait();
        return mBox.returnValue;
    }
}
