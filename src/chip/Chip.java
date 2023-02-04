package chip;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;

public class Chip {

    /**
     * 4kB of 8-bit memory
     * position 0x50: "bios" fontset
     * position 0x200: start of every program
     */
    private char[] memory;
    /**
     * 16 8-bit registers
     * they will be used to store data which is used in several operations
     * register 0xF is used for Carry, Borrow and collision detection
     */
    private char[] V;
    private char I; // 16-bit (only 12 are used) to point to a specific point in memory
    private char pc; // 16-bit program counter to point to the current operation

    /**
     * subroutine callstack
     * allows up to 16 levels of nesting
     */
    private char stack[];
    private int stackPointer; // points to next free slot into stack

    /**
     * delay counters
     * decrement at 60 Hertz
    */
    private int delay_timer; // delay events in programs/games
    private int sound_timer; // to make a beeping sound

    /**
     * input
     * hexadecimal keyboard, 16 keys
     */
    private byte[] keys; // keyboard state

    /**
     * The 64x32 pixel monochrome (black/white) display
     */
    private byte[] display; // 0 for black, 1 for white

    private boolean needRedraw;

    private static final int WIDTH_OF_SCREEN = 64;

    /**
     * reset Chip 8 memory and pointers
     */
    public void init() {
        memory = new char[4096];
        V = new char[16]; // 16-bit register
        I = 0x0;
        pc = 0x200; // initial point where each program will start at 512

        stack = new char[16]; // 16 levels of nesting - can call 16 subroutines
        stackPointer = 0;

        delay_timer = 0;
        sound_timer = 0;

        keys = new byte[16];

        display = new byte[64 * 32]; // dimension
        // test
//        display[0] = 1;
//        display[99] = 1;
        loadFontset();

        needRedraw = false;
    }

    /**
     * Executes a single Operation Code (Opcode)
     */
    public void run() {
        // fetch opcode
        // memory is 8-bits, opcode is 6-bit
        // merge 2 memory slots by shifting 1 to new value by 8 positions left (1 byte)
        char opcode = (char) ((memory[pc] << 8) | memory[pc + 1]); // OR
        System.out.print(Integer.toHexString(opcode) + ": ");
        // decode opcode
        switch(opcode & 0xF000) { // get all values of first nibble
            // eg
            // opcode: 0xD234
            // return: 0xD000
            // first nibble - first 4 byte
            // for opcodes with same first nibble, last byte will tell what to do

            case 0x0000: // Multi-case
                switch(opcode & 0x00FF) {
                    case 0x00E0: // 00E0: Clear screen
                        for(int i = 0; i < display.length; i++) {
                            display[i] = 0; // set all pixels to 0
                        }
                        pc +=2;
                        needRedraw = true;
                        break;

                    case 0x00EE: // 00EE: Returns from subroutine
                        stackPointer--;
                        pc = (char) (stack[stackPointer] + 2); // jump to last subroutine
                        System.out.println("Returning to " + Integer.toHexString(pc).toUpperCase());
                        break;

                    default: // 0NNN: Calls RCA 1802 Program at address NNN
                        System.err.println("Unsupported Opcode!");
                        System.exit(0);
                        break;
                }
                break;

            case 0x1000: { // 1NNN: Jumps to address NNN
                int nnn = opcode & 0x0FFF;
                // no need to add to stack
                pc = (char) nnn;
                System.out.println("Jumping to " + Integer.toHexString(pc).toUpperCase());
                break;
            }

            case 0x2000: // 2NNN: Calls subroutine at NNN
                // eg: 22fc -> call subroutine at 2fc
                stack[stackPointer] = pc;// current address
                stackPointer++; // avoid overwriting
                pc = (char) (opcode & 0x0FFF); // jump to address given by subroutine
                System.out.println("Calling " + Integer.toHexString(pc).toUpperCase() + " from " + Integer.toHexString(stack[stackPointer - 1]).toUpperCase());
                break;

            case 0x3000: { //3XNN: Skips the next instruction if VX equals NN
                int x = (opcode & 0x0F00) >> 8; // 2nd nibble from left
                int nn = (opcode & 0x00FF);
                if(V[x] == nn) {
                    pc += 4;
                    System.out.println("Skipping next instruction (V[" + x + "] == "+ nn + ")");
                } else {
                    pc += 2;
                    System.out.println("Not skipping next instruction (V[" + x + "] != "+ nn + ")");
                }
                break;
            }

            case 0x4000: { // 4XNN: Skip next instruction if VX != NN
                int x = (opcode & 0x0F00) >> 8;
                int nn = opcode & 0x00FF;
                if(V[x] != nn) {
                    System.out.println("Skipping next instruction V[" + x + "] = " + (int)V[x] + " != " + nn);
                    pc += 4;
                } else {
                    System.out.println("Not skipping next instruction V[" + x + "] = " + (int)V[x] + " == " + nn);
                    pc += 2;
                }
                break;
            }

            case 0x5000: { // 5XY0: Skips next instruction if VX equals VY
                int x = (opcode & 0x0F00) >> 8;
                int y = (opcode & 0x00F0) >> 4;
                if(V[x] == V[y]) {
                    System.out.println("Skipping next instruction V[" + x + "] == V[" + y + "]");
                    pc += 4;
                } else {
                    System.out.println("Not skipping next instruction V[" + x + "] != V[" + y + "]");
                    pc += 2;
                }
                break;
            }

            case 0x6000: { // 6XNN: Set VX to NN
                int x = (opcode & 0x0F00) >> 8; // index of register; this case at 3rd spot; shift right 8 positions
                V[x] = (char) (opcode & 0x00FF); // last 2 nibbles of opcode
                pc += 2; // move by 2 bytes since 2 bytes used to make 1 opcode
                System.out.println("Setting V[" + x + "] to " + (int)V[x]);
                break;
            }

            case 0x7000: { // 7XNN: Adds NN to VX
                int x = (opcode & 0x0F00) >> 8;
                int nn = (opcode & 0x00FF);
                V[x] = (char) ((V[x] + nn) & 0xFF); // may overflow or borrow
                pc += 2;
                System.out.println("Adding " + nn + " to V[" + x + "] = " + (int)V[x]);
                break;
            }

            case 0x8000: // contains more data in last nibble

                switch(opcode & 0x000F) {

                    case 0x0000: { // 8XY0: Sets VX to the value of VY
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.println("Set V[" + x + "] to " + (int)V[y]);
                        V[x] = V[y];
                        pc += 2;
                        break;
                    }

                    case 0x0001: { // 8XY1: Sets VX to VX or VY
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.println("Set V[" + x + "] = V[" + x + "] | V[" + y + "]");
                        V[x] = (char) ((V[x] | V[y]) & 0xFF); // & with maximum binary value
                        pc += 2;
                        break;
                    }

                    case 0x0002: { // 8XY2: Sets VX to VX AND VY
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
//                        System.out.println("Set V[" + x + "] to V[" + x + "] = " + (int)V[x] + " & V[" + y + "] = " + (int)V[y] + " = " + (int)(V[x] & V[y]));
                        System.out.println("Set V[" + x + "] to " + (int)V[x] + " & " + (int)V[y] + " = " + (int)(V[x] & V[y]));
                        V[x] = (char) (V[x] & V[y]);
                        pc += 2;
                        break;
                    }

                    case 0x0003: { // 8XY3: Sets VX to VX xor VY
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.println("Set V[" + x + "] = V[" + x + "] ^ V[" + y + "]");
                        V[x] = (char) ((V[x] ^ V[y]) & 0xFF); // & with maximum binary value
                        pc += 2;
                        break;
                    }

                    case 0x0004: { // 8XY4: Adds VY to VX. VF is set to 1 when carry applies else to 0
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.print("Adding V[" + x + "] (" + (int)V[x] + ") + V[" + y + "] (" + (int)V[y] + ") = " + ((V[x] + V[y]) & 0xFF) + ", Apply Carry if needed");
                        // Chip8 stores data in bytes (256 values)
                        // if value is larger than 256, it will subtract 255 since it can't store those
                        if(V[y] > 0xFF - V[x]) { // V[x] + V[y] > 255
                            V[0xF] = 1; // VF
                            System.out.println("Carry!");
                        } else {
                            V[0xF] = 0;
                            System.out.println("No Carry");
                        }
                        V[x] = (char) ((V[x] + V[y]) & 0xFF); // never overflow
                        pc += 2;
                        break;
                    }

                    case 0x0005: { // 8XY5: VY is subtracted from VX. VF is set to 0 when there is a borrow else 1
                        // borrow: if VY > VX, VX - VY will be below 0, not possible due to unsigned bytes
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.print("V[" + x + "] = " + (int)V[x] + "V[" + y + "] = " + (int)V[y] + ", ");
                        if(V[y] >= V[x]) {
                            V[0xF] = 0; // if set to 1, score when ball hits paddle instead of goal
                            System.out.println("Borrow!");
                        } else {
                            V[0xF] = 1;
                            System.out.println("No Borrow");
                        }
                        V[x] = (char) ((V[x] - V[y]) & 0xFF); // & 0xFF to never exceed byte size
                        pc += 2;
                        break;
                    }

                    case 0x0006: { // 8XY6: Shift VX right by one, VF set to the least significant bit of VX before shift
                        int x = (opcode & 0x0F00) >> 8;
                        // LSB is 1, only odd number in power of 2 system
                        // can also use % to define LSB
                        V[0xF] = (char) (V[x] & 0x1); // & with 1
                        V[x] = (char) (V[x] >> 1); // shift right by 1
                        pc += 2;
                        System.out.println("Shift V[" + x + "] >> 1 and VF to LSB of VX");
                        break;
                    }

                    case 0x0007: { // 8XY7: Sets VX to VY minus VX. VF is set to 0 when there's a borrow, and 1 when there isn't
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        if(V[x] > V[y]) {
                            V[0xF] = 1;
                        } else {
                            V[0xF] = 0;
                        }
                        V[x] = (char) ((V[y] - V[x]) & 0xFF);
                        System.out.println("V[" + x + "] = V[" + y + "] - V[" + x + "], applies borrow if needed");
                        pc += 2;
                        break;
                    }

                    case 0x000E: { // 8XYE: Shift VX left by one, VF set to the most significant bit of VX before shift
                        int x = (opcode & 0x0F00) >> 8;
                        // MSB is the leftmost bit that gives most value to the byte
                        V[0xF] = (char) (V[x] & 0x80);
                        V[x] = (char) (V[x] << 1); // shift right by 1
                        pc += 2;
                        System.out.println("Shift V[" + x + "] << 1 and VF to MSB of VX");
                        break;
                    }

                    default:
                        System.err.println("Unsupported Opcode!");
                        System.exit(0);
                        break;
                }

                break;

            case 0x9000: { // 9XY0: Skips next instruction if VX does not equal VY
                int x = (opcode & 0x0F00) >> 8;
                int y = (opcode & 0x00F0) >> 4;
                if(V[x] != V[y]) {
                    System.out.println("Skipping next instruction V[" + x + "] != V[" + y + "]");
                    pc += 4;
                } else {
                    System.out.println("Not skipping next instruction V[" + x + "] == V[" + y + "]");
                    pc += 2;
                }
                break;
            }

            case 0xA000: // ANNN: Set I to NNN
                I = (char) (opcode & 0x0FFF); // address
                pc += 2;
                System.out.println("Set I to " + Integer.toHexString(I).toUpperCase());
                break;

            case 0xB000: { // BNNN: Jumps to the address NNN plus V0
                int nnn = opcode & 0x0FFF;
                int extra = V[0] & 0xFF;
                pc = (char) (nnn + extra);
                break;
            }

            case 0xC000: { // CXNN: Set VX to a random number and NN
                int x = (opcode & 0x0F00) >> 8;
                int nn = (opcode & 0x00FF);
                int randomNumber = new Random().nextInt(256) & nn;
                System.out.println("V[" + x + "] has been set to (randomised) " + randomNumber);
                V[x] = (char) randomNumber;
                pc += 2;
                break; // else it will immediately draw without condition
            }

            case 0xD000: { // DXYN: Draw a sprite (X, Y) size (8, N). Sprite is located at I
                // Default width is 8, goes into single byte of 8 pixels
                // N is the height of image
                // Drawing by XOR-ing to the screen
                // Check collision and set V[0xF]
                // Read image from I
                int x = V[(opcode & 0x0F00) >> 8];
                int y = V[(opcode & 0x00F0) >> 4];
                int height = opcode & 0x000F; // no need shift as last nibble

                V[0xF] = 0; // collision flag

                for(int _y = 0; _y < height; _y++) {
                    int line = memory[I + _y]; // add to address pointer to get to next line of image
                    for(int _x = 0; _x < 8; _x++) {
                        int pixel = line & (0x80 >> _x); // ensures byte is drawn from left to right
                        if(pixel != 0) {
                            int totalX = x + _x;
                            int totalY = y + _y;

                            // screen wrapping
                            // allows paddles to move from top of screen to bottom of screen border
                            // while totalX > 64-> totalX -= 64
                            totalX = totalX % 64; // keep remainder
                            totalY = totalY % 32; // keep remainder

                            // display is 64x32
                            // first pixel on 2nd line - display[64]
                            // first pixel on 3rd line - display[128], so on...
                            int index = (totalY * WIDTH_OF_SCREEN) + totalX;

                            // there is collision
                            if(display[index] == 1) V[0xF] = 1;

                            display[index] ^= 1; // XOR
                        }
                    }
                }
                pc += 2;
                needRedraw = true; // flickering effect
                System.out.println("Drawing at V[" + ((opcode & 0x0F00) >> 8) + "] = " + x + ", V[" + ((opcode & 0x00F0) >> 4) + "] = " + y);
                break;
            }

            case 0xE000: {

                switch(opcode & 0x00FF) {
                    case 0x009E: {// EX9E: Skip next instruction if key VX is pressed
                        int x = (opcode & 0x0F00) >> 8;
                        int key = V[x];
                        if (keys[key] == 1) {
                            pc += 4;
                        } else {
                            pc += 2;
                        }
                        System.out.println("Skipping next instruction if V[" + x + "] = " + ((int)V[x]) + " is pressed");
                        break;
                    }

                    case 0x00A1: {// EXA1: Skip next instruction if key VX is NOT pressed
                        int x = (opcode & 0x0F00) >> 8;
                        int key = V[x];
                        if (keys[key] == 0) {
                            pc += 4;
                        } else {
                            pc += 2;
                        }
                        System.out.println("Skipping next instruction if V[" + x + "] = " + ((int)V[x]) + " is NOT pressed");
                        break;
                    }

                    default:
                        System.err.println("Unexisting opcode");
                        System.exit(0);
                        return;
                }
                break;
            }
            case 0xF000:

                switch(opcode & 0x00FF) {

                    case 0x0007: { // FX07: Set VX to the value of delay_timer
                        int x = (opcode & 0x0F00) >> 8;
                        V[x] = (char) delay_timer;
                        pc += 2;
                        System.out.println("V[" + x + "] has been set to " + delay_timer);
                        break;
                    }

                    case 0x000A: { // FX0A: A key press is awaited and then stored in VX
                        // wait for a key
                        // store it when it's pressed
                        int x = (opcode & 0x0F00) >> 8;
                        for(int i = 0; i < keys.length; i++) { // loop through all keys until the one that's set
                            if(keys[i] == 1) {
                                V[x] = (char)i; // can only be from 0 to 16
                                pc += 2;
                                break;
                            }
                        }
                        System.out.println("Awaiting key press to be stored in V[" + x + "]");
                        break;
                    }

                    case 0x0015: { // FX15: Set delay timer to V[x]
                        int x = (opcode & 0x0F00) >> 8;
                        delay_timer = V[x];
                        pc += 2;
                        System.out.println("Set delay_timer to V[" + x + "] = " + (int) V[x]);
                        break;
                    }

                    case 0x0018: { // FX18: Set sound timer to V[x]
                        int x = (opcode & 0x0F00) >> 8;
                        sound_timer = V[x];
                        pc += 2;
                        break;
                    }

                    case 0x001E: { // FX1E: Adds VX to I
                        int x = (opcode & 0x0F00) >> 8;
                        I = (char) (I + V[x]);
                        System.out.println("Adding V[" + x + "] = " + (int)V[x] + " to I");
                        pc += 2;
                        break;
                    }

                    case 0x0029: { // FX29: Sets I to the location of the sprite for the character VX (fontset)
                        int x = (opcode & 0x0F00) >> 8;
                        int character = V[x];
                        I = (char) (0x050 + (character * 5)); // start from 0x050, increment per digit by 5
                        System.out.println("Setting I to Character V[" + x + "] = " + (int) V[x] + " Offset to 0x" + Integer.toHexString(I).toUpperCase());
                        pc += 2;
                        break;
                    }
                    case 0x0033: { // FX33: Store a binary-coded decimal value VX in I, I + 1 and I + 2
                        int x = (opcode & 0x0F00) >> 8;
                        int value = V[x];
                        // testing
//                        int value = 156;
                        int hundreds = (value - (value % 100)) / 100;
                        value -= (hundreds * 100);
                        int tens = (value - (value % 10)) / 10;
                        value -= (tens * 10);
                        memory[I] = (char) hundreds;
                        memory[I + 1] = (char) tens;
                        memory[I + 2] = (char) value;
                        System.out.println("Storing Binary-Coded Decimal V[" + x + "] = " + (int)(V[(opcode & 0x0F00) >> 8]) + " as {" + hundreds + ", " + tens + ", " + value + "}");
                        pc += 2;
                        break;
                    }

                    case 0x0055: { // FX55: Stores V0 to VX in memory starting at address I
                        int x = (opcode & 0x0F00) >> 8;
                        for(int i = 0; i <= x; i++) { // last point VX inclusive
                            memory[I + i] = V[i];
                        }
                        System.out.println("Setting memory[" + Integer.toHexString(I & 0xFFFF).toUpperCase() + " + n] to the values of V[0] to V[" + x + "]");
                        pc += 2;
                        break;
                    }

                    case 0x0065: // FX65: Files V0 to VX with values from I
                        int x = (opcode & 0x0F00) >> 8;
                        for(int i = 0; i <= x; i++) { // last point VX inclusive
                            V[i] = memory[I + i];
                        }
                        System.out.println("Setting V[0] to V[" + x + "] to the values of memory[0x" + Integer.toHexString(I & 0xFFFF).toUpperCase() + "]");
                        I = (char)(I + x + 1); // original interpreter increments memory pointer
                        pc += 2;
                        break;
                    default:
                        System.err.println("Unsupported Opcode!");
                        System.exit(0);
                }
                break;

            default:
                System.err.println("Unsupported Opcode!");
                System.exit(0);
        }
        // at end of each round, check delay
        if(sound_timer > 0) {
            sound_timer--;
            Audio.playSound("./assets_sound_effect.wav");
        }
        if(delay_timer > 0) {
            delay_timer--;
        }
        // execute opcode
    }

    /**
     * returns display data
     * @return
     * current state of display
     */
    public byte[] getDisplay() {
        return display;
    }

    /**
     * Checks if there is a redraw needed
     * @return
     * If a redraw is needed
     */
    public boolean needsRedraw() {
        return needRedraw;
    }

    /**
     * Notify the chip that is has been redrawn
     */
    public void removeDrawFlag() {
        needRedraw = false;
    }

    /**
     * Loads the program into the memory
     * @param file
     * The location of the program
     */
    public void loadProgram(String file) {
        DataInputStream input = null;
        try {
            // read every byte from file
            input = new DataInputStream(new FileInputStream(new File(file)));

            int offset = 0;
            while(input.available() > 0) {
                // starts at 0x200
                memory[0x200 + offset] = (char)(input.readByte() & 0xFF); // & by 256
                offset++;
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        } finally {
            if(null != input) {
                try { input.close(); } catch (IOException ex) { }
            }
        }
    }

    public void loadFontset() {
        for(int i = 0; i < ChipData.fontset.length; i++) {
            memory[0x50 + i] = (char) (ChipData.fontset[i] & 0xFF); // & by 256
        }
    }

    public void setKeyBuffer(int[] keyBuffer) {
        for(int i = 0; i < keys.length; i++) {
            // copy values of keyBuffer into actual memory of Chip8
            keys[i] = (byte)keyBuffer[i];
        }
    }
}
