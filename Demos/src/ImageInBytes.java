public class ImageInBytes {

    /**
     * @param args
     * 11110000 XXXX
     * 10010000 X  X
     * 10010000 X  X
     * 10010000 X  X
     * 11110000 XXXX
     */
    public static void main(String[] args) {
        int[] IMAGE = { 0xF0, 0x90, 0x90, 0x90, 0xF0 };

        for(int i = 0; i < IMAGE.length; i++) {
            int row = IMAGE[i];

            // binary output
            for (int j = 0; j < 8; j++) {
                int pixel = row & (0x80 >> j);
                if(pixel != 0)
                    System.out.print("1");
                else
                    System.out.print("0");
            }

            // screen output
            System.out.print(" ");
            for (int j = 0; j < 8; j++) {
                int pixel = row & (0x80 >> j);
                if(pixel != 0)
                    System.out.print("X");
                else
                    System.out.print(" ");
            }
            System.out.println();
        }
    }
}
