import java.awt.*;
import java.awt.event.*;
import java.util.Hashtable;


class DefineControls extends Frame implements KeyListener, WindowListener, ActionListener {

    private TextField[] controlsField = new TextField[8];

    private Hashtable<Integer, String> keyNames;

    DefineControls() {
        super("Define Controls");

        keyNames = new Hashtable<Integer, String>();
        keyNames.put(38, "Up arrow");
        keyNames.put(40, "Down arrow");
        keyNames.put(37, "Left arrow");
        keyNames.put(39, "Right arrow");
        keyNames.put(36, "Pad 7");
        keyNames.put(33, "Pad 9");
        keyNames.put(35, "Pad 1");
        keyNames.put(64, "Pad 3");
        keyNames.put(12, "Pad 5");
        keyNames.put(155, "Insert");
        keyNames.put(36, "Home");
        keyNames.put(33, "Page up");
        keyNames.put(127, "Delete");
        keyNames.put(35, "End");
        keyNames.put(34, "Page down");
        keyNames.put(10, "Return");
        keyNames.put(16, "Shift");
        keyNames.put(17, "Control");
        keyNames.put(18, "Alt");
        keyNames.put(32, "Space");
        keyNames.put(20, "Caps lock");
        keyNames.put(8, "Backspace");

        GridLayout g = new GridLayout(9, 2, 12, 12);

        setLayout(g);

        controlsField[0] = addControlsLine("D-pad up:");
        controlsField[1] = addControlsLine("D-pad down:");
        controlsField[2] = addControlsLine("D-pad left:");
        controlsField[3] = addControlsLine("D-pad right:");
        controlsField[4] = addControlsLine("A button:");
        controlsField[5] = addControlsLine("B button:");
        controlsField[6] = addControlsLine("Start button:");
        controlsField[7] = addControlsLine("Select button:");

        for (int r = 0; r < 8; r++) {
            controlsField[r].setText(getKeyDesc(JavaBoy.keyCodes[r], (char) JavaBoy.keyCodes[r])
                    + " (" + JavaBoy.keyCodes[r] + ")");
        }

        Button cancel = new Button("Close");
        cancel.setActionCommand("Controls close");
        cancel.addActionListener(this);
        add(cancel);

        setSize(230, 300);
        setResizable(false);
        addWindowListener(this);
        setVisible(true);
    }

    private String getKeyDesc(int code, char c) {
        if (keyNames.containsKey(code)) {
            return keyNames.get(code);
        } else {
            return c + "";
        }
    }

    private TextField addControlsLine(String name) {
        add(new Label(name));
        TextField t = new TextField(4);
        t.addKeyListener(this);
        add(t);
        return t;
    }

    public void keyPressed(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
        System.out.println(e.getKeyCode() + ", " + e.getKeyChar());

        for (int r = 0; r < 8; r++) {
            if (e.getSource() == controlsField[r]) {
                controlsField[r].setText(getKeyDesc(e.getKeyCode(), e.getKeyChar()) + " (" + e.getKeyCode() + ")");
                JavaBoy.keyCodes[r] = e.getKeyCode();
            }
        }
    }

    public void keyTyped(KeyEvent e) {

    }

    public void windowClosed(WindowEvent e) {
    }

    public void windowClosing(WindowEvent e) {
        setVisible(false);
    }

    public void windowOpened(WindowEvent e) {
    }

    public void windowIconified(WindowEvent e) {
    }

    public void windowDeiconified(WindowEvent e) {
    }

    public void windowActivated(WindowEvent e) {
    }

    public void windowDeactivated(WindowEvent e) {
    }

    public void actionPerformed(ActionEvent e) {
        setVisible(false);
    }

}