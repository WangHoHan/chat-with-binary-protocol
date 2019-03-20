import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Scanner;


public class Client implements Runnable
{
    private static boolean accept_statement = false; //flaga komendy !accept
    private static boolean invite_statement = true; //flaga komendy !invite
    private static boolean reject_statement = false; //flaga komendy !reject
    private boolean statement = true; //warunek czytania pakietu
    private DataInputStream data_input_stream;
    private DataOutputStream data_output_stream;
    private long session_id; //id sesji


    private Client(String ip, int port) //konstruktor klasy Client
    {
        try
        {
            Socket client_socket = new Socket(ip, port);

            data_input_stream = new DataInputStream(client_socket.getInputStream());
            data_output_stream = new DataOutputStream(client_socket.getOutputStream());
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
        String message; //pole wiadomości

        byte[] message_length_bytes_table = new byte[8];
        byte[] message_bytes_table = new byte[packet.length - 10];
        byte[] session_id_bytes_table = new byte[8];

        operation = ((packet[0] & 0b11110000) >> 4); //dekodowanie pola operacji
        answer = ((packet[0] & 0b00001110) >> 1); //dekodowanie pola odpowiedzi

        for(int i = 0; i < 8; i++)
        {
            message_length_bytes_table[i] = (byte) ((packet[i] & 0b00000001) << 7);
            message_length_bytes_table[i] = (byte) (message_length_bytes_table[i] | ((packet[i + 1] & 0b11111110) >> 1));
        }

        for(int i = 8; i < (packet.length - 2); i++)
        {
            message_bytes_table[i - 8] = (byte) ((packet[i] & 0b00000001) << 7);
            message_bytes_table[i - 8] = (byte) (message_bytes_table[i - 8] | ((packet[i + 1] & 0b11111110) >> 1));
        }

        message = new String(message_bytes_table); //dekodowanie wiadomości za pomocą tablicy bajtów utworzonej dla danego słowa

        session_id_bytes_table[6] = (byte) (packet[packet.length - 2] & 0b00000001);
        session_id_bytes_table[7] = (byte) (packet[packet.length - 1] & 0b11111111);

        session_id = bytesToLong(session_id_bytes_table); //dekodowanie id sesji

        if(operation == 0 && answer == 0) {} //pakiet inicjalizacyjny wysyłany przez serwer
        else if(operation == 0 && answer == 1)
        {
            System.out.println("friend: " + message); //odczytywanie wiadomości od drugiego klienta
        }
        else if(operation == 1 && answer == 2) //odczytywanie odpowiedzi serwera na akceptacje zaproszenia ze strony drugiego klienta
        {
            accept_statement = false;
            invite_statement = false;
            reject_statement = false;

            System.out.println(message);
        }
        else if(operation == 2 && answer == 3) //odczytywanie odpowiedzi serwera na sprawdzanie dostępności drugiego klienta
        {
            System.out.println(message);
        }
        else if(operation == 3 && answer == 4) //odczytywanie odpowiedzi serwera na rozłączenie przez drugiego klienta
        {
            accept_statement = false;
            invite_statement = true;
            reject_statement = false;

            System.out.println(message);
        }
        else if(operation == 4 && answer == 5) //odczytywanie odpowiedzi serwera na opuszczenie czatu przez drugiego klienta
        {
            statement = false;

            System.out.println(message);
        }
        else if(operation == 5 && answer == 6) //odczytywanie odpowiedzi serwera na zaproszenie do czatu przez drugiego klienta
        {
            accept_statement = true;
            invite_statement = false;
            reject_statement = true;

            System.out.println(message);
        }
        else if(operation == 6 && answer == 7) //odczytywanie odpowiedzi serwera na odrzucenie zaproszenia ze strony drugiego klienta
        {
            accept_statement = false;
            invite_statement = true;
            reject_statement = false;

            System.out.println(message);
        }
    }

    private byte[] generatePacket(int operation, long session_id, String message) //funkcja generująca i kodująca pakiet do postaci binarnej
    {
        int answer = 0; //pole odpowiedzi

        byte[] packet = new byte[10 + message.length()]; //ustalenie długości pakietu na podstawie długości słowa

        long message_length = message.length();

        byte[] length_table = longToBytes(message_length);
        byte[] message_table = new byte[message.length()];
        byte[] session_id_table = longToBytes(session_id);

        packet[0] = (byte) ((operation & 0b00001111) << 4); //kodowanie pola operacji
        packet[0] = (byte) (packet[0] | (byte) ((answer  & 0b00000111) << 1)); //kodowanie pola odpowiedzi
        packet[0] = (byte) (packet[0] | (byte) ((length_table[0]) & 0b10000000) >> 7); //kodowanie liczby określającej długość słowa (linia od 150 do 165)
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

        packet[packet.length - 2] = (byte) (packet[packet.length - 2] | ((session_id_table[6]) & 0b00000001)); //kodowanie id sesji (linia 175 do 176)
        packet[packet.length - 1] = (byte) (packet[packet.length - 1] | ((session_id_table[7]) & 0b11111111));

        return packet; //zwracamy gotowy, zakodowany pakiet
    }

    private byte[] longToBytes(long x) //funkcja zamieniająca zmienną typu long na tablicę statyczną bajtów
    {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);

        return buffer.array();
    }

    private void readPacket() throws Exception //metoda odczytująca pakiety
    {
        int message_length1;
        int statement_value;
        long message_length;

        byte[] packet = new byte[9];

        statement_value = data_input_stream.read(packet, 0, 9); //odczytujemy pierwsze 9 bajtów pakietu co umożliwi nam odczytanie całego rozmiaru pakietu i pobranie go

        byte[] message_length_bytes_table = new byte[8];

        for (int i = 0; i < 8; i++)
        {
            message_length_bytes_table[i] = (byte) ((packet[i] & 0b00000001) << 7);
            message_length_bytes_table[i] = (byte) (message_length_bytes_table[i] | ((packet[i + 1] & 0b11111110) >> 1));
        }

        message_length = bytesToLong(message_length_bytes_table);

        message_length1 = (int) message_length;

        byte[] packet1 = new byte[10 + message_length1];

        System.arraycopy(packet, 0, packet1, 0, packet.length);

        data_input_stream.read(packet1, 9, message_length1 + 1);

        if(statement_value == -1) //statement_value ustawi się na -1 w momencie gdy nie będzie miał więcej pakietów do pobrania
        {
            statement = false; //zakończy działanie funkcji run()
        }
        else
        {
            decodePacket(packet1); //wysłanie pakietu do metody dekodującej
        }
    }

    private void writePacket(int operation, String message, long session_id) //metoda wysyłająca pakiety
    {
        int packet_length = 10 + message.length(); //tworzenie rozmiaru pakietu na podstawie rozmiaru wiadomości

        try
        {
            data_output_stream.write(generatePacket(operation, session_id, message), 0, packet_length); //wysłanie pakietu do serwera
        }
        catch(Exception exception) {}
    }


    public static void main(String args[]) throws Exception //main
    {
        boolean loop_exit_statement = false; //zmienna boolowska umożliwiająca nam wyjście z pętli

        Client client = new Client("127.0.0.1",1234); //tworzenie obiektu klienta

        Thread thread =new Thread(client); //tworzenie wątku

        thread.start(); //wystartowanie wątku

        while(!loop_exit_statement) //warunek wyjścia z pętli
        {
            int operation = 0; //przypisujemy wartość 0, aby za każdym kolejnym wykonaniem się pętli wartość się zerowała

            String message; //wiadomość

            Scanner scanner = new Scanner(System.in);

            message = scanner.nextLine(); //wczytanie wiadomości od użytkownika

            switch(message)
            {
                case "!accept": //komenda !accept
                {
                    operation = 1;

                    if(accept_statement) //warunek akceptacji zaproszenia
                    {
                        System.out.println("INFO: You have accepted second users invite.");

                        accept_statement = false;
                        invite_statement = false;
                        reject_statement = false;

                        client.writePacket(operation, message, client.session_id);
                    }

                    break;
                }
                case "!available": //komenda !available
                {
                    operation = 2;

                    System.out.println("INFO: Request about second user availability been sent to the server. No reply from the server means that second user is unavailable.");

                    client.writePacket(operation, message, client.session_id);

                    break;
                }
                case "!disconnect": //komenda !disconnect
                {
                    operation = 3;

                    System.out.println("INFO: You have disconnected from the chat. Send an invite to connect with second user again.");

                    accept_statement = false;
                    invite_statement = true;
                    reject_statement = false;

                    client.writePacket(operation, message, client.session_id);

                    break;
                }
                case "!exit": //komenda !exit
                {
                    operation = 4;

                    System.out.println("INFO: You have left the chat.");

                    client.writePacket(operation, message, client.session_id);

                    loop_exit_statement = true;

                    break;
                }
                case "!invite": //komenda !invite
                {
                    operation = 5;

                    if(invite_statement) //warunek wysłania zaproszenia
                    {
                        System.out.println("INFO: You have invited second user to the chat.");

                        accept_statement = false;
                        invite_statement = false;
                        reject_statement = false;

                        client.writePacket(operation, message, client.session_id);
                    }

                    break;
                }
                case "!reject": //komenda !reject
                {
                    operation = 6;

                    if(reject_statement) //warunek odrzucenia zaproszenia
                    {
                        System.out.println("INFO: You have rejected second users invite.");

                        accept_statement = false;
                        invite_statement = true;
                        reject_statement = false;

                        client.writePacket(operation, message, client.session_id);
                    }

                    break;
                }
            }

            if(operation == 0)
            {
                client.writePacket(operation, message, client.session_id); //wysłanie wiadomości
            }
        }

        thread.join();
    }
}