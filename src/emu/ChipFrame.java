package emu;

import chip.Chip;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class ChipFrame extends JFrame implements KeyListener {

    private static final long serialVersionUID = 1L;
    private ChipPanel panel;
    private int[] keyBuffer; // actual key state memory, only signed up at start of each operation
    private int[] keyIdToKey;

    public ChipFrame(Chip c) {
        /**
         * window to update and fit all elements inside viewport
         * if too small, extend
         * if too large, check preferred size or shrink to minimum
         * define insets
         * borders are calculated in preferred size, hence smaller and needs insets
         */
        setPreferredSize(new Dimension(640, 320));
        pack();
        setPreferredSize(new Dimension(640 + getInsets().left + getInsets().right, 320 + getInsets().top + getInsets().bottom));
        panel = new ChipPanel(c);
        setLayout(new BorderLayout()); // to fit entire window with chip panel
        add(panel, BorderLayout.CENTER);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle("Chip 8 Emulator");
        pack();
        setVisible(true);
        addKeyListener(this); // enable keyword controller

        keyIdToKey = new int[256]; // array that maps each button
        keyBuffer = new int[16];
        fillKeyIds();
    }

    private void fillKeyIds() {
        for(int i = 0; i < keyIdToKey.length; i++) {
            keyIdToKey[i] = -1; // all other states set to -1
        }
        keyIdToKey['1'] = 1;
        keyIdToKey['2'] = 2;
        keyIdToKey['3'] = 3;
        keyIdToKey['Q'] = 4;
        keyIdToKey['W'] = 5; // V[5]
        keyIdToKey['E'] = 6;
        keyIdToKey['A'] = 7;
        keyIdToKey['S'] = 8;
        keyIdToKey['D'] = 9;
        keyIdToKey['Z'] = 0xA;
        keyIdToKey['X'] = 0;
        keyIdToKey['C'] = 0xB;
        keyIdToKey['4'] = 0xC;
        keyIdToKey['R'] = 0xD;
        keyIdToKey['F'] = 0xE;
        keyIdToKey['V'] = 0xF;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if(keyIdToKey[e.getKeyCode()] != -1) { // user pressed actual keypad input
            keyBuffer[keyIdToKey[e.getKeyCode()]] = 1; // pressed
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if(keyIdToKey[e.getKeyCode()] != -1) {
            keyBuffer[keyIdToKey[e.getKeyCode()]] = 0; // released
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    public int[] getKeyBuffer() {
        return keyBuffer;
    }
}
