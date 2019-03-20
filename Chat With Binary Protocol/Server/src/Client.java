import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;


public class Client implements Runnable
{
    private boolean client_connection_acceptance = false; //warunek przesyłania wiadomości między klientami
    private boolean statement = true; //warunek czytania pakietu
    private Client partner;
    private DataInputStream data_input_stream;
    private DataOutputStream data_output_stream;
    private Socket client_socket;


    public Client(ServerSocket server_socket, int client_number, int session_id) //konstruktor klasy Client
    {
        try
        {
            client_socket = server_socket.accept();

            data_input_stream = new DataInputStream(client_socket.getInputStream());
            data_output_stream = new DataOutputStream(client_socket.getOutputStream());

            System.out.println("Client " + client_number + " connected.");
            System.out.println("Session ID has been generated for Client " + client_number + ": " + session_id + ".");

            writePacket(0, 0, "Initialization packet had been sent to Client " + client_number + ".", session_id); //wysyłanie inicjalizującego pakietu do klienta, klient otrzymuje unikalne id sesji
        }
        catch(Exception exception) {}
    }


    @Override
    public void run() //metoda czytająca pakiety tak długo jak nadchodzą
    {
        try
        {
            while(statement) //warunek odczytywania pakietów
            {
                readPacket();
            }
        }
        catch(Exception exception) {}
    }


    private long bytesToLong(byte[] bytes) //funkcja zamieniająca statyczną tablicę bajtów na zmienną typu long
    {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        buffer.flip();

        return buffer.getLong();
    }

    private void decodePacket(byte[] packet) //metoda dekodująca pakiety
    {
        int answer; //pole odpowiedzi
        int operation; //pole operacji
        long session_id; //pole id sesji
        long message_length; //pole długości wiadomości
        String message; //pole wiadomości

        byte[] message_length_bytes_table = new byte[8];
        byte[] message_bytes_table = new byte[packet.length - 10];
        byte[] session_id_bytes_table = new byte[8];

        operation = ((packet[0] & 0b11110000) >> 4); //dekodowanie pola operacji
        answer = ((packet[0] & 0b00001110) >> 1); //dekodowanie pola odpowiedzi

        for (int i = 0; i < 8; i++)
        {
            message_length_bytes_table[i] = (byte) ((packet[i] & 0b00000001) << 7);
            message_length_bytes_table[i] = (byte) (message_length_bytes_table[i] | ((packet[i + 1] & 0b11111110) >> 1));
        }

        message_length = bytesToLong(message_length_bytes_table); //dekodowanie długości słowa

        for (int i = 8; i < (packet.length - 2); i++)
        {
            message_bytes_table[i - 8] = (byte) ((packet[i] & 0b00000001) << 7);
            message_bytes_table[i - 8] = (byte) (message_bytes_table[i - 8] | ((packet[i + 1] & 0b11111110) >> 1));
        }

        message = new String(message_bytes_table); //dekodowanie wiadomości za pomocą tablicy bajtów utworzonej dla danego słowa

        session_id_bytes_table[6] = (byte) (packet[packet.length - 2] & 0b00000001);
        session_id_bytes_table[7] = (byte) (packet[packet.length - 1] & 0b11111111);

        session_id = bytesToLong(session_id_bytes_table); //dekodowanie id sesji

        if(operation == 0 && client_connection_acceptance)
        {
            partner.writePacket(operation, 1, message, session_id); //wysyłanie wiadomości napisanej przez jednego z klientów
        }
        else if(operation == 1)
        {
            client_connection_acceptance = true;

            partner.client_connection_acceptance = true;

            partner.writePacket(operation, 2, "SERVER: Second user had accepted your invite.", session_id); //wysyłanie informacji o akceptacji zaproszenia przez jednego z klientów
        }
        else if(operation == 2)
        {
            boolean client_availability;

            client_availability = partner.getClientSocket().isClosed();

            if(!client_availability)
            {
                writePacket(operation, 3, "SERVER: Second user is available.", session_id); //wysyłanie informacji o dostępności drugiego klienta
            }
        }
        else if(operation == 3)
        {
            client_connection_acceptance = false;
            partner.client_connection_acceptance = false;

            partner.writePacket(operation, 4, "SERVER: Second user have disconnected from the chat. Send an invite to connect with second user again.", session_id); //wysyłanie informacji o rozłączeniu się z czatu jednego z klientów
        }
        else if(operation == 4)
        {
            client_connection_acceptance = false;
            partner.client_connection_acceptance = false;

            partner.writePacket(operation, 5, "SERVER: Second user left the chat.", session_id); //wysyłanie informacji o opuszczeniu czatu przez jednego z klientów
        }
        else if(operation == 5)
        {
            partner.writePacket(operation, 6, "SERVER: Second user have invited you to the chat.", session_id); //wysyłanie informacji o zaproszeniu do czatu przez jednego z klientów
        }
        else if(operation == 6)
        {
            client_connection_acceptance = false;
            partner.client_connection_acceptance = false;

            partner.writePacket(operation, 7, "SERVER: Second user had rejected your invite.", session_id); //wysyłanie informacji o odrzuceniu zaproszenia przez jednego z klientów
        }

        System.out.println("<DECODE> Operation: " + operation);
        System.out.println("<DECODE> Answer: " + answer);
        System.out.println("<DECODE> Message length: " + message_length);
        System.out.println("<DECODE> Message: " + message);
        System.out.println("<DECODE> Session ID: " + session_id);
    }

    private byte[] generatePacket(int operation, int answer, long session_id, String message) //funkcja generująca i kodująca pakiet do postaci binarnej
    {
        byte[] packet = new byte[10 + message.length()]; //ustalenie długości pakietu na podstawie długości słowa

        long message_length = message.length();

        byte[] length_table = longToBytes(message_length);
        byte[] message_table = new byte[message.length()];
        byte[] session_id_table = longToBytes(session_id);

        packet[0] = (byte) ((operation & 0b00001111) << 4); //kodowanie pola operacji
        packet[0] = (byte) (packet[0] | (byte) ((answer  & 0b00000111) << 1)); //kodowanie pola odpowiedzi
        packet[0] = (byte) (packet[0] | (byte) ((length_table[0]) & 0b10000000) >> 7); //kodowanie liczby określającej długość słowa (linia od 163 do 178)
        packet[1] = (byte) (((length_table[0]) & 0b01111111) << 1);
        packet[1] = (byte) (packet[1] | (byte) ((length_table[1]) & 0b10000000) >> 7);
        packet[2] = (byte) (((length_table[1]) & 0b01111111) << 1);
        packet[2] = (byte) (packet[2] | (byte) ((length_table[2]) & 0b10000000) >> 7);
        packet[3] = (byte) (((length_table[2]) & 0b01111111) << 1);
        packet[3] = (byte) (packet[3] | (byte) ((length_table[3]) & 0b10000000) >> 7);
        packet[4] = (byte) (((length_table[3]) & 0b01111111) << 1);
        packet[4] = (byte) (packet[4] | (byte) ((length_table[4]) & 0b10000000) >> 7);
        packet[5] = (byte) (((length_table[4]) & 0b01111111) << 1);
        packet[5] = (byte) (packet[5] | (byte) ((length_table[5]) & 0b10000000) >> 7);
        packet[6] = (byte) (((length_table[5]) & 0b01111111) << 1);
        packet[6] = (byte) (packet[6] | (byte) ((length_table[6]) & 0b10000000) >> 7);
        packet[7] = (byte) (((length_table[6]) & 0b01111111) << 1);
        packet[7] = (byte) (packet[7] | (byte) ((length_table[7]) & 0b10000000) >> 7);
        packet[8] = (byte) (((length_table[7]) & 0b01111111) << 1);

        System.arraycopy(message.getBytes(), 0, message_table, 0, message.length());

        for(int i = 8; i < (packet.length - 2); i++) //kodowanie wiadomości
        {
            packet[i] = (byte) (packet[i] | ((message_table[i - 8]) & 0b10000000) >> 7);
            packet[i + 1] = (byte) (((message_table[i - 8]) & 0b01111111) << 1);
        }

        packet[packet.length - 2] = (byte) (packet[packet.length - 2] | ((session_id_table[6]) & 0b00000001)); //kodowanie id sesji (linia od 188 do 189)
        packet[packet.length - 1] = (byte) (packet[packet.length - 1] | ((session_id_table[7]) & 0b11111111));

        System.out.println("<CODE> Operation: " + operation);
        System.out.println("<CODE> Answer: " + answer);
        System.out.println("<CODE> Message length: " + message_length);
        System.out.println("<CODE> Message: " + message);
        System.out.println("<CODE> Session ID: " + session_id);

        return packet; //zwracamy gotowy, zakodowany pakiet
    }

    public Client getClient() //getter dla Clienta
    {
        return this;
    }

    private Socket getClientSocket() //getter dla ClientSocketa
    {
        return client_socket;
    }

    private byte[] longToBytes(long x) //funkcja zamieniająca zmienną typu long na tablicę statyczną bajtów
    {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);

        return buffer.array();
    }

    public void makePartner(Client partner) //metoda tworząca partnera
    {
        this.partner = partner;
    }

    private void readPacket() throws Exception //metoda odczytująca pakiety
    {
        int statement_value;

        byte[] packet = new byte[9];

        statement_value = data_input_stream.read(packet, 0, 9); //odczytujemy pierwsze 9 bajtów pakietu co umożliwi nam odczytanie całego rozmiaru pakietu i pobranie go

        long message_length;

        byte[] message_length_bytes_table = new byte[8];

        for (int i = 0; i < 8; i++)
        {
            message_length_bytes_table[i] = (byte) ((packet[i] & 0b00000001) << 7);
            message_length_bytes_table[i] = (byte) (message_length_bytes_table[i] | ((packet[i + 1] & 0b11111110) >> 1));
        }

        message_length = bytesToLong(message_length_bytes_table);

        int message_length_1 = (int) message_length;

        byte[] packet1 = new byte[10 + message_length_1];

        System.arraycopy(packet, 0, packet1, 0, packet.length);

        data_input_stream.read(packet1, 9, message_length_1 + 1);

        if(statement_value == -1) //statement_value ustawi się na -1 w momencie gdy nie będzie miał więcej pakietów do pobrania
        {
            statement = false; //zakończy działanie funkcji run()
        }
        else
        {
            decodePacket(packet1); //wysłanie pakietu do metody dekodującej
        }
    }

    private void writePacket(int operation, int answer, String message, long session_id) //metoda wysyłająca pakiety
    {
        int packet_length = 10 + message.length(); //tworzenie rozmiaru pakietu na podstawie rozmiaru wiadomości

        try
        {
            data_output_stream.write(generatePacket(operation, answer, session_id, message),0, packet_length); //wysłanie pakietu do klienta
        }
        catch(Exception exception) {}
    }
}