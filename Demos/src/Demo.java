import java.awt.*;

public class Demo {

    public static void main(String[] args) {

        final byte[] memory = { 0x22, (byte)0xFC };

        System.out.println(Integer.toHexString(memory[0]) + " -> " + memory[0]); // 34
        System.out.println(Integer.toHexString(memory[1]) + " -> " + memory[1]); // fffffffc
        System.out.println(Integer.toHexString(memory[1]) + " -> " + (int) memory[1]); // fffffffc
        System.out.println(Integer.toHexString(memory[1] & 0xFF) + " -> " + (memory[1] & 0xFF)); // 252 calculation casts an intger that can store values > 200

        /**
         *  fffffffc
         *  000000FF
         *  left with last 2 bytes
         */
    }
}
