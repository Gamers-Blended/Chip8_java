package emu;

import javax.swing.*;
import java.awt.*;

import chip.Chip;

public class ChipPanel extends JPanel {

    private Chip chip;

    public ChipPanel(Chip chip) {
        this.chip = chip;
    }

    public void paint(Graphics g) {
        byte[] display = chip.getDisplay();
        for(int i = 0; i < display.length; i++) {
           if(display[i] == 0) // set color
               g.setColor(Color.BLACK);
           else
               g.setColor(Color.WHITE);

            /** coordinates of pixel
             * 64 pixel width
             * eg pixel 65 = first pixel on 2nd row
             */
           int x = (i % 64);
           int y = (int)Math.floor(i / 64);

           g.fillRect(x*10, y*10, 10, 10); // dimensions upscaled 10x
        }
    }
}
